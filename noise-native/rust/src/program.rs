//! Evaluator for `SignedNoiseProgram` (Java): a CNG's signed fast path as data. Mirrors
//! `SignedNoiseProgram.sample2D/3D`, `OffsetSimplexSource` and `SimplexOctaves` operation for operation.

use crate::batch::{fill_simplex_2d, fill_simplex_3d};

pub const LONGS_PER_NODE: usize = 3;
pub const DOUBLES_PER_NODE: usize = 7;
pub const MAXIMUM_NODES: usize = 16;
pub const MAXIMUM_OCTAVES: i64 = 16;
const UNITY_OPACITY: f64 = 1.0;
const OCTAVE_FREQUENCY_STEP: f64 = 2.0;
const OCTAVE_AMPLITUDE_STEP: f64 = 0.5;

#[derive(Clone, Copy, Debug, PartialEq)]
pub struct Octaves {
    pub seed: i64,
    pub frequency: f64,
    pub count: i64,
    pub bounding: f64,
}

#[derive(Clone, Copy, Debug, PartialEq)]
pub struct Node {
    pub octaves: Octaves,
    pub offset: Option<(f64, f64)>,
    pub scale: f64,
    pub opacity: f64,
    pub fracture_strength: f64,
}

#[derive(Debug, PartialEq)]
pub struct Program {
    nodes: Vec<Node>,
}

#[derive(Debug, PartialEq, Eq)]
pub enum ProgramError {
    NodeCount,
    OctaveCount,
    SliceLength,
}

impl Program {
    pub fn decode(longs: &[i64], doubles: &[f64], node_count: usize) -> Result<Program, ProgramError> {
        check_layout(longs, doubles, node_count)?;
        let nodes = (0..node_count).map(|index| decode_node(longs, doubles, index));
        Ok(Program { nodes: nodes.collect::<Result<Vec<Node>, ProgramError>>()? })
    }

    pub fn fill_2d(&self, xs: &[f64], zs: &[f64], out: &mut [f64]) {
        fill_node_2d(&self.nodes, 0, [xs, zs], out);
    }

    pub fn fill_3d(&self, xs: &[f64], ys: &[f64], zs: &[f64], out: &mut [f64]) {
        fill_node_3d(&self.nodes, 0, [xs, ys, zs], out);
    }
}

fn check_layout(longs: &[i64], doubles: &[f64], node_count: usize) -> Result<(), ProgramError> {
    if node_count == 0 || node_count > MAXIMUM_NODES {
        return Err(ProgramError::NodeCount);
    }
    let fits = longs.len() == node_count * LONGS_PER_NODE && doubles.len() == node_count * DOUBLES_PER_NODE;
    if fits { Ok(()) } else { Err(ProgramError::SliceLength) }
}

fn decode_node(longs: &[i64], doubles: &[f64], index: usize) -> Result<Node, ProgramError> {
    let ints = &longs[index * LONGS_PER_NODE..][..LONGS_PER_NODE];
    let reals = &doubles[index * DOUBLES_PER_NODE..][..DOUBLES_PER_NODE];
    let octaves = decode_octaves(ints, reals)?;
    let offset = (ints[0] != 0).then_some((reals[2], reals[3]));
    Ok(Node { octaves, offset, scale: reals[4], opacity: reals[5], fracture_strength: reals[6] })
}

fn decode_octaves(ints: &[i64], reals: &[f64]) -> Result<Octaves, ProgramError> {
    if !(1..=MAXIMUM_OCTAVES).contains(&ints[2]) {
        return Err(ProgramError::OctaveCount);
    }
    Ok(Octaves { seed: ints[1], frequency: reals[0], count: ints[2], bounding: reals[1] })
}

fn fill_node_2d(nodes: &[Node], index: usize, [xs, zs]: [&[f64]; 2], out: &mut [f64]) {
    let node = &nodes[index];
    if index + 1 == nodes.len() {
        return fill_source_2d(node, [&scaled(xs, node.scale), &scaled(zs, node.scale)], out);
    }
    let warped_x = warped(nodes, index, xs, |field| fill_node_2d(nodes, index + 1, [xs, zs], field));
    let warped_z = warped(nodes, index, zs, |field| fill_node_2d(nodes, index + 1, [zs, xs], field));
    fill_source_2d(node, [&scaled(&warped_x, node.scale), &scaled(&warped_z, node.scale)], out);
}

fn fill_node_3d(nodes: &[Node], index: usize, [xs, ys, zs]: [&[f64]; 3], out: &mut [f64]) {
    let node = &nodes[index];
    if index + 1 == nodes.len() {
        let scaled_axes = [scaled(xs, node.scale), scaled(ys, node.scale), scaled(zs, node.scale)];
        return fill_source_3d(node, [&scaled_axes[0], &scaled_axes[1], &scaled_axes[2]], out);
    }
    let warped_axes = warped_volume(nodes, index, [xs, ys, zs]);
    let scaled_axes = warped_axes.map(|axis| scaled(&axis, node.scale));
    fill_source_3d(node, [&scaled_axes[0], &scaled_axes[1], &scaled_axes[2]], out);
}

fn warped_volume(nodes: &[Node], index: usize, [xs, ys, zs]: [&[f64]; 3]) -> [Vec<f64>; 3] {
    let child = index + 1;
    let warped_x = warped(nodes, index, xs, |field| fill_node_3d(nodes, child, [xs, ys, zs], field));
    let warped_y = warped(nodes, index, ys, |field| fill_node_2d(nodes, child, [ys, xs], field));
    let warped_z = warped(nodes, index, zs, |field| fill_node_3d(nodes, child, [zs, xs, ys], field));
    [warped_x, warped_y, warped_z]
}

fn warped(nodes: &[Node], index: usize, axis: &[f64], fill_fracture: impl FnOnce(&mut [f64])) -> Vec<f64> {
    let mut field = vec![0.0; axis.len()];
    fill_fracture(&mut field);
    let strength = nodes[index].fracture_strength;
    axis.iter().zip(&field).map(|(&coordinate, &fracture)| coordinate + (fracture * strength)).collect()
}

fn scaled(axis: &[f64], factor: f64) -> Vec<f64> {
    axis.iter().map(|&coordinate| coordinate * factor).collect()
}

fn shifted(axis: &[f64], offset: f64) -> Vec<f64> {
    axis.iter().map(|&coordinate| coordinate + offset).collect()
}

fn fill_source_2d(node: &Node, [xs, zs]: [&[f64]; 2], out: &mut [f64]) {
    match node.offset {
        Some((offset_x, offset_z)) => fill_octaves_2d(&node.octaves, [&shifted(xs, offset_x), &shifted(zs, offset_z)], out),
        None => fill_octaves_2d(&node.octaves, [xs, zs], out),
    }
    apply_opacity(node.opacity, out);
}

fn fill_source_3d(node: &Node, [xs, ys, zs]: [&[f64]; 3], out: &mut [f64]) {
    match node.offset {
        Some((offset_x, offset_z)) => fill_octaves_3d(&node.octaves, [&shifted(xs, offset_x), ys, &shifted(zs, offset_z)], out),
        None => fill_octaves_3d(&node.octaves, [xs, ys, zs], out),
    }
    apply_opacity(node.opacity, out);
}

fn apply_opacity(opacity: f64, values: &mut [f64]) {
    if opacity != UNITY_OPACITY {
        values.iter_mut().for_each(|value| *value = ((*value + 1.0) * opacity) - 1.0);
    }
}

fn fill_octaves_2d(octaves: &Octaves, [xs, zs]: [&[f64]; 2], out: &mut [f64]) {
    let single = |factor: Option<f64>, field: &mut [f64]| {
        let frequency = factor_then_frequency(factor, octaves.frequency);
        fill_simplex_2d(octaves.seed, &frequency(xs), &frequency(zs), field);
    };
    sum_octaves(octaves, out, single);
}

fn fill_octaves_3d(octaves: &Octaves, [xs, ys, zs]: [&[f64]; 3], out: &mut [f64]) {
    let single = |factor: Option<f64>, field: &mut [f64]| {
        let frequency = factor_then_frequency(factor, octaves.frequency);
        fill_simplex_3d(octaves.seed, [&frequency(xs), &frequency(ys), &frequency(zs)], field);
    };
    sum_octaves(octaves, out, single);
}

/// SimplexNoise scales by the octave factor, then FastNoiseDouble.GetSimplex by its frequency; a single
/// octave skips the factor multiply entirely, exactly like the Java branch.
fn factor_then_frequency(factor: Option<f64>, frequency: f64) -> impl Fn(&[f64]) -> Vec<f64> {
    move |axis| match factor {
        Some(factor) => axis.iter().map(|&coordinate| (coordinate * factor) * frequency).collect(),
        None => axis.iter().map(|&coordinate| coordinate * frequency).collect(),
    }
}

fn sum_octaves(octaves: &Octaves, out: &mut [f64], single: impl Fn(Option<f64>, &mut [f64])) {
    if octaves.count == 1 {
        return single(None, out);
    }
    out.fill(0.0);
    let (mut factor, mut amplitude) = (1.0, 1.0);
    let mut field = vec![0.0; out.len()];
    for _ in 0..octaves.count {
        single(Some(factor), &mut field);
        out.iter_mut().zip(&field).for_each(|(value, &sample)| *value += sample * amplitude);
        factor *= OCTAVE_FREQUENCY_STEP;
        amplitude *= OCTAVE_AMPLITUDE_STEP;
    }
    out.iter_mut().for_each(|value| *value *= octaves.bounding);
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::simplex3d::simplex_3d;

    fn single_node(octaves: i64) -> (Vec<i64>, Vec<f64>) {
        (vec![1, 42, octaves], vec![0.01, 1.0, 100.0, -50.0, 0.5, 1.0, 0.0])
    }

    #[test]
    fn zero_or_too_many_nodes_are_refused() {
        assert_eq!(Program::decode(&[], &[], 0), Err(ProgramError::NodeCount));
        assert_eq!(Program::decode(&[], &[], MAXIMUM_NODES + 1), Err(ProgramError::NodeCount));
    }

    #[test]
    fn slices_that_do_not_match_the_node_count_are_refused() {
        let (longs, doubles) = single_node(1);
        assert_eq!(Program::decode(&longs[..2], &doubles, 1), Err(ProgramError::SliceLength));
        assert_eq!(Program::decode(&longs, &doubles, 2), Err(ProgramError::SliceLength));
    }

    #[test]
    fn octave_counts_outside_the_java_clamp_are_refused() {
        for octaves in [0, -1, MAXIMUM_OCTAVES + 1] {
            let (longs, doubles) = single_node(octaves);
            assert_eq!(Program::decode(&longs, &doubles, 1), Err(ProgramError::OctaveCount));
        }
    }

    #[test]
    fn offset_shifts_x_and_z_but_never_y_in_three_dimensions() {
        let (longs, doubles) = single_node(1);
        let program = Program::decode(&longs, &doubles, 1).expect("valid single node");
        let expected = simplex_3d(42, (1.0 * 0.5 + 100.0) * 0.01, (2.0 * 0.5) * 0.01, (3.0 * 0.5 - 50.0) * 0.01);
        let mut out = [0.0];
        program.fill_3d(&[1.0], &[2.0], &[3.0], &mut out);
        assert_eq!(out[0], expected);
    }

    #[test]
    fn non_unity_opacity_rescales_around_minus_one_and_unity_leaves_values_alone() {
        let mut values = [1.0, -1.0];
        apply_opacity(0.5, &mut values);
        assert_eq!(values, [0.0, -1.0]);
        apply_opacity(UNITY_OPACITY, &mut values);
        assert_eq!(values, [0.0, -1.0]);
    }
}

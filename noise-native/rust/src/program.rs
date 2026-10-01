//! Evaluator for `SignedNoiseProgram` (Java): a CNG's signed fast path as data. Mirrors
//! `SignedNoiseProgram.sample2D/3D`, `OffsetSimplexSource` and `SimplexOctaves` operation for operation.

use crate::simplex2d::simplex_2d;
use crate::simplex3d::simplex_3d;

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
        if node_count == 0 || node_count > MAXIMUM_NODES {
            return Err(ProgramError::NodeCount);
        }
        let fits = longs.len() == node_count * LONGS_PER_NODE && doubles.len() == node_count * DOUBLES_PER_NODE;
        if !fits {
            return Err(ProgramError::SliceLength);
        }
        let nodes: Result<Vec<Node>, ProgramError> = (0..node_count).map(|index| decode_node(longs, doubles, index)).collect();
        Ok(Program { nodes: nodes? })
    }

    pub fn sample_2d(&self, x: f64, z: f64) -> f64 {
        sample_node_2d(&self.nodes, 0, x, z)
    }

    pub fn sample_3d(&self, x: f64, y: f64, z: f64) -> f64 {
        sample_node_3d(&self.nodes, 0, x, y, z)
    }
}

fn decode_node(longs: &[i64], doubles: &[f64], index: usize) -> Result<Node, ProgramError> {
    let ints = &longs[index * LONGS_PER_NODE..][..LONGS_PER_NODE];
    let reals = &doubles[index * DOUBLES_PER_NODE..][..DOUBLES_PER_NODE];
    if !(1..=MAXIMUM_OCTAVES).contains(&ints[2]) {
        return Err(ProgramError::OctaveCount);
    }
    let octaves = Octaves { seed: ints[1], frequency: reals[0], count: ints[2], bounding: reals[1] };
    let offset = (ints[0] != 0).then_some((reals[2], reals[3]));
    Ok(Node { octaves, offset, scale: reals[4], opacity: reals[5], fracture_strength: reals[6] })
}

fn sample_node_2d(nodes: &[Node], index: usize, x: f64, z: f64) -> f64 {
    let node = &nodes[index];
    if index + 1 == nodes.len() {
        return with_opacity(node, source_2d(node, x * node.scale, z * node.scale));
    }
    let fx = x + (sample_node_2d(nodes, index + 1, x, z) * node.fracture_strength);
    let fz = z + (sample_node_2d(nodes, index + 1, z, x) * node.fracture_strength);
    with_opacity(node, source_2d(node, fx * node.scale, fz * node.scale))
}

fn sample_node_3d(nodes: &[Node], index: usize, x: f64, y: f64, z: f64) -> f64 {
    let node = &nodes[index];
    if index + 1 == nodes.len() {
        return with_opacity(node, source_3d(node, x * node.scale, y * node.scale, z * node.scale));
    }
    let fx = x + (sample_node_3d(nodes, index + 1, x, y, z) * node.fracture_strength);
    let fy = y + (sample_node_2d(nodes, index + 1, y, x) * node.fracture_strength);
    let fz = z + (sample_node_3d(nodes, index + 1, z, x, y) * node.fracture_strength);
    with_opacity(node, source_3d(node, fx * node.scale, fy * node.scale, fz * node.scale))
}

fn with_opacity(node: &Node, signed: f64) -> f64 {
    if node.opacity == UNITY_OPACITY { signed } else { ((signed + 1.0) * node.opacity) - 1.0 }
}

fn source_2d(node: &Node, x: f64, z: f64) -> f64 {
    match node.offset {
        Some((offset_x, offset_z)) => octaves_2d(&node.octaves, x + offset_x, z + offset_z),
        None => octaves_2d(&node.octaves, x, z),
    }
}

fn source_3d(node: &Node, x: f64, y: f64, z: f64) -> f64 {
    match node.offset {
        Some((offset_x, offset_z)) => octaves_3d(&node.octaves, x + offset_x, y, z + offset_z),
        None => octaves_3d(&node.octaves, x, y, z),
    }
}

fn octaves_2d(octaves: &Octaves, x: f64, z: f64) -> f64 {
    let single = |x: f64, z: f64| simplex_2d(octaves.seed, x * octaves.frequency, z * octaves.frequency);
    if octaves.count == 1 {
        return single(x, z);
    }
    octave_sum(octaves.count, |factor| single(x * factor, z * factor)) * octaves.bounding
}

fn octaves_3d(octaves: &Octaves, x: f64, y: f64, z: f64) -> f64 {
    let frequency = octaves.frequency;
    let single = |x: f64, y: f64, z: f64| simplex_3d(octaves.seed, x * frequency, y * frequency, z * frequency);
    if octaves.count == 1 {
        return single(x, y, z);
    }
    octave_sum(octaves.count, |factor| single(x * factor, y * factor, z * factor)) * octaves.bounding
}

fn octave_sum(count: i64, octave: impl Fn(f64) -> f64) -> f64 {
    let (mut factor, mut amplitude, mut value) = (1.0, 1.0, 0.0);
    for _ in 0..count {
        value += octave(factor) * amplitude;
        factor *= OCTAVE_FREQUENCY_STEP;
        amplitude *= OCTAVE_AMPLITUDE_STEP;
    }
    value
}

#[cfg(test)]
mod tests {
    use super::*;

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
        assert_eq!(program.sample_3d(1.0, 2.0, 3.0), expected);
    }

    #[test]
    fn non_unity_opacity_rescales_around_minus_one() {
        let mut node = Node { octaves: Octaves { seed: 1, frequency: 1.0, count: 1, bounding: 1.0 },
            offset: None, scale: 1.0, opacity: 0.5, fracture_strength: 0.0 };
        assert_eq!(with_opacity(&node, 1.0), 0.0);
        node.opacity = UNITY_OPACITY;
        assert_eq!(with_opacity(&node, 0.3), 0.3);
    }
}

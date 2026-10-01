//! Evaluator for `UnsignedNoiseProgram` (Java): CNG's unsigned 2D path (noiseFast2D). Mirrors
//! `UnsignedNoiseProgram.sample2D` operation for operation, over whole arrays.

use crate::program::{ProgramError, Source, check_layout, decode_source, fill_signed_source_2d, node_slices, scaled};

pub const UNSIGNED_LONGS_PER_NODE: usize = 4;
pub const UNSIGNED_DOUBLES_PER_NODE: usize = 10;
const STRIDES: (usize, usize) = (UNSIGNED_LONGS_PER_NODE, UNSIGNED_DOUBLES_PER_NODE);
const SIGNED_TO_UNSIGNED_DIVISOR: f64 = 2.0;
const SIGNED_TO_UNSIGNED_OFFSET: f64 = 0.5;
const SINGLE_SOURCE_WEIGHT: f64 = 1.0;

#[derive(Clone, Copy, Debug, PartialEq)]
pub struct PostAdjustment {
    pub down: f64,
    pub up: f64,
    pub patch: f64,
}

#[derive(Clone, Copy, Debug, PartialEq)]
pub struct UnsignedNode {
    pub source: Source,
    pub scale: f64,
    pub opacity: f64,
    pub fracture_scale: f64,
    pub post: Option<PostAdjustment>,
}

#[derive(Debug, PartialEq)]
pub struct UnsignedProgram {
    nodes: Vec<UnsignedNode>,
}

impl UnsignedProgram {
    pub fn decode(longs: &[i64], doubles: &[f64], node_count: usize) -> Result<UnsignedProgram, ProgramError> {
        check_layout(longs, doubles, node_count, STRIDES)?;
        let nodes = (0..node_count).map(|index| decode_unsigned_node(longs, doubles, index));
        Ok(UnsignedProgram { nodes: nodes.collect::<Result<Vec<UnsignedNode>, ProgramError>>()? })
    }

    pub fn fill_2d(&self, xs: &[f64], zs: &[f64], out: &mut [f64]) {
        fill_unsigned_node_2d(&self.nodes, 0, [xs, zs], out);
    }
}

fn decode_unsigned_node(longs: &[i64], doubles: &[f64], index: usize) -> Result<UnsignedNode, ProgramError> {
    let (ints, reals) = node_slices(longs, doubles, index, STRIDES);
    let (scale, opacity, fracture_scale) = (reals[4], reals[5], reals[6]);
    Ok(UnsignedNode { source: decode_source(ints, reals)?, scale, opacity, fracture_scale, post: decode_post(ints, reals) })
}

fn decode_post(ints: &[i64], reals: &[f64]) -> Option<PostAdjustment> {
    (ints[3] != 0).then_some(PostAdjustment { down: reals[7], up: reals[8], patch: reals[9] })
}

fn fill_unsigned_node_2d(nodes: &[UnsignedNode], index: usize, [xs, zs]: [&[f64]; 2], out: &mut [f64]) {
    let node = &nodes[index];
    if index + 1 == nodes.len() {
        fill_unsigned_at(node, [&scaled(xs, node.scale), &scaled(zs, node.scale)], out);
    } else {
        let warped_x = warped(node, xs, |field| fill_unsigned_node_2d(nodes, index + 1, [xs, zs], field));
        let warped_z = warped(node, zs, |field| fill_unsigned_node_2d(nodes, index + 1, [zs, xs], field));
        fill_unsigned_at(node, [&scaled(&warped_x, node.scale), &scaled(&warped_z, node.scale)], out);
    }
    if let Some(post) = node.post {
        apply_post(post, out);
    }
}

fn warped(node: &UnsignedNode, axis: &[f64], fill_fracture: impl FnOnce(&mut [f64])) -> Vec<f64> {
    let mut field = vec![0.0; axis.len()];
    fill_fracture(&mut field);
    let fracture_scale = node.fracture_scale;
    axis.iter().zip(&field).map(|(&coordinate, &fracture)| coordinate + ((fracture - SIGNED_TO_UNSIGNED_OFFSET) * fracture_scale)).collect()
}

fn fill_unsigned_at(node: &UnsignedNode, axes: [&[f64]; 2], out: &mut [f64]) {
    fill_signed_source_2d(&node.source, axes, out);
    let opacity = node.opacity;
    out.iter_mut().for_each(|value| *value = ((*value / SIGNED_TO_UNSIGNED_DIVISOR) + SIGNED_TO_UNSIGNED_OFFSET) * opacity);
}

fn apply_post(post: PostAdjustment, values: &mut [f64]) {
    values.iter_mut().for_each(|value| *value = ((*value / SINGLE_SOURCE_WEIGHT) - post.down + post.up) * post.patch);
}

#[cfg(test)]
mod tests {
    use super::*;

    fn single_node(post_flag: i64) -> (Vec<i64>, Vec<f64>) {
        (vec![0, 42, 1, post_flag], vec![0.01, 1.0, 0.0, 0.0, 1.0, 1.0, 1.0, 0.0, 0.0, 1.0])
    }

    #[test]
    fn identity_post_turns_negative_zero_into_positive_zero_like_java() {
        let mut values = [-0.0];
        apply_post(PostAdjustment { down: 0.0, up: 0.0, patch: 1.0 }, &mut values);
        assert_eq!(values[0].to_bits(), 0.0_f64.to_bits());
    }

    #[test]
    fn post_flag_off_leaves_the_unsigned_value_untouched() {
        let (longs, doubles) = single_node(0);
        let program = UnsignedProgram::decode(&longs, &doubles, 1).expect("valid node");
        assert_eq!(program.nodes[0].post, None);
    }

    #[test]
    fn unsigned_layout_rejects_signed_strides() {
        let (longs, doubles) = single_node(1);
        assert_eq!(UnsignedProgram::decode(&longs[..3], &doubles[..7], 1), Err(ProgramError::SliceLength));
    }
}

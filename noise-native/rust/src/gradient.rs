//! Gradient lookups: the dot product of a hashed lattice gradient with the corner offset.

use crate::hash::{hash_2d, hash_3d};

const GRADIENTS_2D: [f64; 16] = [
    -1.0, -1.0, 1.0, -1.0, -1.0, 1.0, 1.0, 1.0, 0.0, -1.0, -1.0, 0.0, 0.0, 1.0, 1.0, 0.0,
];
const GRADIENTS_3D: [f64; 48] = [
    1.0, 1.0, 0.0, -1.0, 1.0, 0.0, 1.0, -1.0, 0.0, -1.0, -1.0, 0.0, //
    1.0, 0.0, 1.0, -1.0, 0.0, 1.0, 1.0, 0.0, -1.0, -1.0, 0.0, -1.0, //
    0.0, 1.0, 1.0, 0.0, -1.0, 1.0, 0.0, 1.0, -1.0, 0.0, -1.0, -1.0, //
    1.0, 1.0, 0.0, 0.0, -1.0, 1.0, -1.0, 1.0, 0.0, 0.0, -1.0, -1.0,
];
const GRADIENT_2D_MASK: i64 = 7;
const GRADIENT_3D_MASK: i64 = 15;

#[inline(always)]
pub fn gradient_dot_2d(seed: i64, lattice: [i64; 2], offset: [f64; 2]) -> f64 {
    let index = gradient_index_2d(hash_2d(seed, lattice[0], lattice[1]));
    offset[0] * GRADIENTS_2D[index] + offset[1] * GRADIENTS_2D[index + 1]
}

#[inline(always)]
pub fn gradient_dot_3d(seed: i64, lattice: [i64; 3], offset: [f64; 3]) -> f64 {
    let index = gradient_index_3d(hash_3d(seed, lattice[0], lattice[1], lattice[2]));
    let xy = offset[0] * GRADIENTS_3D[index] + offset[1] * GRADIENTS_3D[index + 1];
    xy + offset[2] * GRADIENTS_3D[index + 2]
}

#[inline(always)]
fn gradient_index_2d(hash: i64) -> usize {
    ((hash & GRADIENT_2D_MASK) as usize) << 1
}

#[inline(always)]
fn gradient_index_3d(hash: i64) -> usize {
    ((hash & GRADIENT_3D_MASK) as usize) * 3
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn gradient_indices_stay_inside_their_tables_for_negative_hashes() {
        for hash in [i64::MIN, -1, -7, -15, i64::MAX] {
            assert!(gradient_index_2d(hash) + 1 < GRADIENTS_2D.len());
            assert!(gradient_index_3d(hash) + 2 < GRADIENTS_3D.len());
        }
    }

    #[test]
    fn zero_offset_has_zero_contribution_whatever_the_gradient() {
        assert_eq!(gradient_dot_2d(42, [i64::MAX, i64::MIN], [0.0, 0.0]), 0.0);
        assert_eq!(gradient_dot_3d(42, [-1, -1, -1], [0.0, 0.0, 0.0]), 0.0);
    }
}

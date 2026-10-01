//! 2D simplex noise, `FastNoiseDouble.SingleSimplex(long, double, double)`.

use crate::gradient::gradient_dot_2d;

const SKEW_2D: f64 = 0.3660254037844386;
const UNSKEW_2D: f64 = 0.21132486540518713;
const CORNER_RADIUS_SQUARED_2D: f64 = 0.5;
const OUTPUT_SCALE_2D: f64 = 50.0;

#[inline(always)]
pub fn simplex_2d(seed: i64, x: f64, y: f64) -> f64 {
    let lattice = lattice_origin_2d(x, y);
    let origin = offset_from_lattice_origin_2d(lattice, x, y);
    OUTPUT_SCALE_2D * sum_corner_contributions_2d(seed, lattice, origin)
}

#[inline(always)]
pub fn floor_to_long(value: f64) -> i64 {
    value.floor() as i64
}

#[inline(always)]
fn lattice_origin_2d(x: f64, y: f64) -> [i64; 2] {
    let skew = (x + y) * SKEW_2D;
    [floor_to_long(x + skew), floor_to_long(y + skew)]
}

#[inline(always)]
fn offset_from_lattice_origin_2d(lattice: [i64; 2], x: f64, y: f64) -> [f64; 2] {
    let unskew = lattice[0].wrapping_add(lattice[1]) as f64 * UNSKEW_2D;
    [x - (lattice[0] as f64 - unskew), y - (lattice[1] as f64 - unskew)]
}

#[inline(always)]
fn sum_corner_contributions_2d(seed: i64, lattice: [i64; 2], origin: [f64; 2]) -> f64 {
    let step = middle_corner_step_2d(origin);
    let near = corner_contribution_2d(seed, lattice, origin);
    let middle = corner_contribution_2d(seed, add_2d(lattice, step), middle_offset_2d(origin, step));
    near + middle + corner_contribution_2d(seed, add_2d(lattice, [1, 1]), far_offset_2d(origin))
}

#[inline(always)]
fn middle_corner_step_2d(origin: [f64; 2]) -> [i64; 2] {
    if origin[0] > origin[1] { [1, 0] } else { [0, 1] }
}

#[inline(always)]
fn middle_offset_2d(origin: [f64; 2], step: [i64; 2]) -> [f64; 2] {
    [origin[0] - step[0] as f64 + UNSKEW_2D, origin[1] - step[1] as f64 + UNSKEW_2D]
}

#[inline(always)]
fn far_offset_2d(origin: [f64; 2]) -> [f64; 2] {
    [origin[0] - 1.0 + 2.0 * UNSKEW_2D, origin[1] - 1.0 + 2.0 * UNSKEW_2D]
}

#[inline(always)]
fn add_2d(lattice: [i64; 2], step: [i64; 2]) -> [i64; 2] {
    [lattice[0].wrapping_add(step[0]), lattice[1].wrapping_add(step[1])]
}

#[inline(always)]
fn corner_contribution_2d(seed: i64, lattice: [i64; 2], offset: [f64; 2]) -> f64 {
    let falloff = CORNER_RADIUS_SQUARED_2D - offset[0] * offset[0] - offset[1] * offset[1];
    let squared = falloff * falloff;
    let contribution = squared * squared * gradient_dot_2d(seed, lattice, offset);
    if falloff < 0.0 { 0.0 } else { contribution }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn skew_constants_match_the_java_runtime_expressions() {
        assert_eq!(SKEW_2D, 0.5 * (3.0_f64.sqrt() - 1.0));
        assert_eq!(UNSKEW_2D, (3.0 - 3.0_f64.sqrt()) / 6.0);
    }

    #[test]
    fn floor_to_long_saturates_and_maps_nan_to_zero_like_a_java_long_cast() {
        assert_eq!(floor_to_long(f64::NAN), 0);
        assert_eq!(floor_to_long(f64::INFINITY), i64::MAX);
        assert_eq!(floor_to_long(f64::NEG_INFINITY), i64::MIN);
        assert_eq!(floor_to_long(-0.5), -1);
    }

    #[test]
    fn lattice_points_sample_to_zero() {
        assert_eq!(simplex_2d(1337, 0.0, 0.0), 0.0);
    }

    #[test]
    fn output_stays_within_the_unit_range() {
        for step in 0..20_000 {
            let value = simplex_2d(7, step as f64 * 0.173, step as f64 * -0.311);
            assert!((-1.0..=1.0).contains(&value), "{value} out of range at step {step}");
        }
    }

    #[test]
    fn nan_input_propagates_instead_of_turning_into_a_number() {
        assert!(simplex_2d(1, f64::NAN, 0.25).is_nan());
    }
}

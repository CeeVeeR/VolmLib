//! 3D simplex noise, `FastNoiseDouble.SingleSimplex(long, double, double, double)`.

use crate::gradient::gradient_dot_3d;
use crate::simplex2d::floor_to_long;

const SKEW_3D: f64 = 1.0 / 3.0;
const UNSKEW_3D: f64 = 1.0 / 6.0;
const FAR_CORNER_UNSKEW_3D: f64 = UNSKEW_3D * 3.0 - 1.0;
const CORNER_RADIUS_SQUARED_3D: f64 = 0.6;
const OUTPUT_SCALE_3D: f64 = 32.0;

type CornerSteps = ([i64; 3], [i64; 3]);

#[inline(always)]
pub fn simplex_3d(seed: i64, x: f64, y: f64, z: f64) -> f64 {
    let lattice = lattice_origin_3d(x, y, z);
    let origin = offset_from_lattice_origin_3d(lattice, x, y, z);
    let steps = inner_corner_steps_3d(origin);
    let inner = inner_contributions_3d(seed, lattice, origin, steps);
    OUTPUT_SCALE_3D * (inner + far_contribution_3d(seed, lattice, origin))
}

#[inline(always)]
fn lattice_origin_3d(x: f64, y: f64, z: f64) -> [i64; 3] {
    let skew = (x + y + z) * SKEW_3D;
    [floor_to_long(x + skew), floor_to_long(y + skew), floor_to_long(z + skew)]
}

#[inline(always)]
fn offset_from_lattice_origin_3d(lattice: [i64; 3], x: f64, y: f64, z: f64) -> [f64; 3] {
    let sum = lattice[0].wrapping_add(lattice[1]).wrapping_add(lattice[2]);
    let unskew = sum as f64 * UNSKEW_3D;
    let corner = |axis: usize| lattice[axis] as f64 - unskew;
    [x - corner(0), y - corner(1), z - corner(2)]
}

/// Branch-free form of FastNoise's simplex ordering table, so batches vectorize. It keeps Java's
/// exact comparisons (`>=` when x leads, `<` otherwise) because they differ for NaN.
#[inline(always)]
fn inner_corner_steps_3d(origin: [f64; 3]) -> CornerSteps {
    let [x, y, z] = origin;
    let x_leads = x >= y;
    let (y_ge_z, x_ge_z, y_lt_z, x_lt_z) = (y >= z, x >= z, y < z, x < z);
    let first = first_step_3d(x_leads, y_ge_z, x_ge_z, y_lt_z);
    (first, second_step_3d(x_leads, y_ge_z, y_lt_z, x_lt_z))
}

#[inline(always)]
fn first_step_3d(x_leads: bool, y_ge_z: bool, x_ge_z: bool, y_lt_z: bool) -> [i64; 3] {
    let k = (x_leads && !y_ge_z && !x_ge_z) || (!x_leads && y_lt_z);
    [step(x_leads && (y_ge_z || x_ge_z)), step(!x_leads && !y_lt_z), step(k)]
}

#[inline(always)]
fn second_step_3d(x_leads: bool, y_ge_z: bool, y_lt_z: bool, x_lt_z: bool) -> [i64; 3] {
    let i = x_leads || (!y_lt_z && !x_lt_z);
    let k = (x_leads && !y_ge_z) || (!x_leads && (y_lt_z || x_lt_z));
    [step(i), step(y_ge_z || !x_leads), step(k)]
}

#[inline(always)]
fn step(taken: bool) -> i64 {
    i64::from(taken)
}

#[inline(always)]
fn inner_contributions_3d(seed: i64, lattice: [i64; 3], origin: [f64; 3], steps: CornerSteps) -> f64 {
    let near = corner_contribution_3d(seed, lattice, origin);
    let first = corner_contribution_3d(seed, add_3d(lattice, steps.0), stepped(origin, steps.0, UNSKEW_3D));
    near + first + corner_contribution_3d(seed, add_3d(lattice, steps.1), stepped(origin, steps.1, SKEW_3D))
}

#[inline(always)]
fn far_contribution_3d(seed: i64, lattice: [i64; 3], origin: [f64; 3]) -> f64 {
    let offset = origin.map(|axis| axis + FAR_CORNER_UNSKEW_3D);
    corner_contribution_3d(seed, add_3d(lattice, [1, 1, 1]), offset)
}

#[inline(always)]
fn stepped(origin: [f64; 3], step: [i64; 3], unskew: f64) -> [f64; 3] {
    [0, 1, 2].map(|axis| origin[axis] - step[axis] as f64 + unskew)
}

#[inline(always)]
fn add_3d(lattice: [i64; 3], step: [i64; 3]) -> [i64; 3] {
    [0, 1, 2].map(|axis| lattice[axis].wrapping_add(step[axis]))
}

#[inline(always)]
fn corner_contribution_3d(seed: i64, lattice: [i64; 3], offset: [f64; 3]) -> f64 {
    let [x, y, z] = offset;
    let falloff = CORNER_RADIUS_SQUARED_3D - x * x - y * y - z * z;
    let squared = falloff * falloff;
    let contribution = squared * squared * gradient_dot_3d(seed, lattice, offset);
    if falloff < 0.0 { 0.0 } else { contribution }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn far_corner_unskew_matches_the_java_constant() {
        assert_eq!(FAR_CORNER_UNSKEW_3D, -0.5);
    }

    fn java_ordering_table(origin: [f64; 3]) -> CornerSteps {
        let [x, y, z] = origin;
        if x >= y {
            if y >= z { ([1, 0, 0], [1, 1, 0]) } else if x >= z { ([1, 0, 0], [1, 0, 1]) } else { ([0, 0, 1], [1, 0, 1]) }
        } else if y < z {
            ([0, 0, 1], [0, 1, 1])
        } else if x < z {
            ([0, 1, 0], [0, 1, 1])
        } else {
            ([0, 1, 0], [1, 1, 0])
        }
    }

    #[test]
    fn branch_free_steps_equal_the_java_table_for_every_ordering_tie_and_nan() {
        let values = [-1.0, 0.0, 1.0, f64::NAN];
        for x in values {
            for y in values {
                for z in values {
                    assert_eq!(inner_corner_steps_3d([x, y, z]), java_ordering_table([x, y, z]), "{x} {y} {z}");
                }
            }
        }
    }

    #[test]
    fn nan_offsets_take_the_same_branch_as_java_comparisons() {
        assert_eq!(inner_corner_steps_3d([f64::NAN, 0.0, 0.0]), ([0, 1, 0], [1, 1, 0]));
    }

    #[test]
    fn every_ordering_of_the_offsets_picks_a_distinct_simplex() {
        let orders = [[3.0, 2.0, 1.0], [3.0, 1.0, 2.0], [2.0, 1.0, 3.0],
            [1.0, 2.0, 3.0], [1.0, 3.0, 2.0], [2.0, 3.0, 1.0]];
        let mut seen: Vec<CornerSteps> = orders.map(inner_corner_steps_3d).to_vec();
        seen.sort();
        seen.dedup();
        assert_eq!(seen.len(), orders.len());
    }

    #[test]
    fn output_stays_within_the_unit_range() {
        for step in 0..20_000 {
            let t = step as f64;
            let value = simplex_3d(-9, t * 0.173, t * -0.071, t * 0.297);
            assert!((-1.0..=1.0).contains(&value), "{value} out of range at step {step}");
        }
    }
}

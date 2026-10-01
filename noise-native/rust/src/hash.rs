//! FastNoise lattice hash with Java `long` semantics (wrapping multiply, arithmetic shift).

const X_PRIME: i64 = 1619;
const Y_PRIME: i64 = 31337;
const Z_PRIME: i64 = 6971;
const AVALANCHE_MULTIPLIER: i64 = 60493;
const AVALANCHE_SHIFT: u32 = 13;

#[inline(always)]
pub fn hash_2d(seed: i64, x: i64, y: i64) -> i64 {
    avalanche(seed ^ X_PRIME.wrapping_mul(x) ^ Y_PRIME.wrapping_mul(y))
}

#[inline(always)]
pub fn hash_3d(seed: i64, x: i64, y: i64, z: i64) -> i64 {
    avalanche(hash_seed_3d(seed, x, y, z))
}

#[inline(always)]
fn hash_seed_3d(seed: i64, x: i64, y: i64, z: i64) -> i64 {
    seed ^ X_PRIME.wrapping_mul(x) ^ Y_PRIME.wrapping_mul(y) ^ Z_PRIME.wrapping_mul(z)
}

#[inline(always)]
fn avalanche(mixed: i64) -> i64 {
    let cubed = mixed.wrapping_mul(mixed).wrapping_mul(mixed);
    let spread = cubed.wrapping_mul(AVALANCHE_MULTIPLIER);
    (spread >> AVALANCHE_SHIFT) ^ spread
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn hash_wraps_instead_of_panicking_at_extreme_coordinates() {
        assert_eq!(hash_3d(0, i64::MIN, i64::MIN, i64::MIN), 0, "odd prime * MIN wraps to MIN, MIN^2 wraps to 0");
        assert!(hash_2d(i64::MAX, i64::MAX, i64::MIN) >= 0);
    }

    #[test]
    fn arithmetic_shift_xor_clears_the_sign_bit_so_hashes_are_never_negative() {
        let negative = (-5000..5000).map(|x| hash_3d(1337, x, -x, x * 7)).filter(|h| *h < 0).count();
        assert_eq!(negative, 0, "a logical shift would leak the sign bit into the hash");
    }

    #[test]
    fn hash_depends_on_every_axis_and_the_seed() {
        let base = hash_3d(1, 2, 3, 4);
        assert_ne!(base, hash_3d(2, 2, 3, 4));
        assert_ne!(base, hash_3d(1, 3, 3, 4));
        assert_ne!(base, hash_3d(1, 2, 4, 4));
        assert_ne!(base, hash_3d(1, 2, 3, 5));
    }
}

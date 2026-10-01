//! Slice fills written as plain zipped loops so LLVM can vectorize them. Every helper on the
//! sampling path is `#[inline(always)]`: one call left inside the loop blocks vectorization.

use crate::program::Program;
use crate::simplex2d::simplex_2d;
use crate::simplex3d::simplex_3d;

pub fn fill_simplex_2d(seed: i64, xs: &[f64], ys: &[f64], out: &mut [f64]) {
    for ((slot, &x), &y) in out.iter_mut().zip(xs).zip(ys) {
        *slot = simplex_2d(seed, x, y);
    }
}

pub fn fill_simplex_3d(seed: i64, inputs: [&[f64]; 3], out: &mut [f64]) {
    let points = inputs[0].iter().zip(inputs[1]).zip(inputs[2]);
    for (slot, ((&x, &y), &z)) in out.iter_mut().zip(points) {
        *slot = simplex_3d(seed, x, y, z);
    }
}

pub fn fill_program_2d(program: &Program, xs: &[f64], zs: &[f64], out: &mut [f64]) {
    program.fill_2d(xs, zs, out);
}

pub fn fill_program_3d(program: &Program, inputs: [&[f64]; 3], out: &mut [f64]) {
    program.fill_3d(inputs[0], inputs[1], inputs[2], out);
}

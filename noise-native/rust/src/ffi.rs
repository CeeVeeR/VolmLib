//! C ABI for Java's foreign function API. The only `unsafe` in the crate lives here: borrowing
//! the arrays Java hands over as slices, after every precondition was checked.

use crate::batch::{fill_program_2d, fill_program_3d, fill_simplex_2d, fill_simplex_3d};
use crate::program::{DOUBLES_PER_NODE, LONGS_PER_NODE, MAXIMUM_NODES, Program};
use crate::unsigned::{UNSIGNED_DOUBLES_PER_NODE, UNSIGNED_LONGS_PER_NODE, UnsignedProgram};
use crate::simplex2d::simplex_2d;
use crate::simplex3d::simplex_3d;

pub const STATUS_OK: i32 = 0;
pub const STATUS_NULL_POINTER: i32 = 1;
pub const STATUS_TOO_LONG: i32 = 2;
pub const STATUS_OUTPUT_ALIASES_INPUT: i32 = 3;
pub const STATUS_INVALID_PROGRAM: i32 = 4;

const DOUBLE_BYTES: usize = size_of::<f64>();

#[unsafe(no_mangle)]
pub extern "C" fn volmlib_noise_simplex_2d(seed: i64, x: f64, y: f64) -> f64 {
    simplex_2d(seed, x, y)
}

#[unsafe(no_mangle)]
pub extern "C" fn volmlib_noise_simplex_3d(seed: i64, x: f64, y: f64, z: f64) -> f64 {
    simplex_3d(seed, x, y, z)
}

#[unsafe(no_mangle)]
pub extern "C" fn volmlib_noise_simplex_2d_fill(seed: i64, xs: *const f64, ys: *const f64, out: *mut f64, len: usize) -> i32 {
    match checked_buffers(&[xs, ys], out, len) {
        Ok((inputs, output)) => {
            fill_simplex_2d(seed, inputs[0], inputs[1], output);
            STATUS_OK
        }
        Err(status) => status,
    }
}

#[unsafe(no_mangle)]
pub extern "C" fn volmlib_noise_simplex_3d_fill(seed: i64, xs: *const f64, ys: *const f64, zs: *const f64, out: *mut f64, len: usize) -> i32 {
    match checked_buffers(&[xs, ys, zs], out, len) {
        Ok((ins, output)) => {
            fill_simplex_3d(seed, [ins[0], ins[1], ins[2]], output);
            STATUS_OK
        }
        Err(status) => status,
    }
}

#[unsafe(no_mangle)]
pub extern "C" fn volmlib_noise_program_fill_2d(longs: *const i64, doubles: *const f64, nodes: usize, xs: *const f64, zs: *const f64, out: *mut f64, len: usize) -> i32 {
    let program = match decoded_program(longs, doubles, nodes) {
        Ok(program) => program,
        Err(status) => return status,
    };
    checked_buffers(&[xs, zs], out, len).map_or_else(|status| status, |(ins, output)| {
        fill_program_2d(&program, ins[0], ins[1], output);
        STATUS_OK
    })
}

#[unsafe(no_mangle)]
pub extern "C" fn volmlib_noise_program_fill_3d(longs: *const i64, doubles: *const f64, nodes: usize, xs: *const f64, ys: *const f64, zs: *const f64, out: *mut f64, len: usize) -> i32 {
    let program = match decoded_program(longs, doubles, nodes) {
        Ok(program) => program,
        Err(status) => return status,
    };
    checked_buffers(&[xs, ys, zs], out, len).map_or_else(|status| status, |(ins, output)| {
        fill_program_3d(&program, [ins[0], ins[1], ins[2]], output);
        STATUS_OK
    })
}

#[unsafe(no_mangle)]
pub extern "C" fn volmlib_noise_unsigned_fill_2d(longs: *const i64, doubles: *const f64, nodes: usize, xs: *const f64, zs: *const f64, out: *mut f64, len: usize) -> i32 {
    let program = match decoded_unsigned_program(longs, doubles, nodes) {
        Ok(program) => program,
        Err(status) => return status,
    };
    checked_buffers(&[xs, zs], out, len).map_or_else(|status| status, |(ins, output)| {
        program.fill_2d(ins[0], ins[1], output);
        STATUS_OK
    })
}

fn decoded_unsigned_program(longs: *const i64, doubles: *const f64, nodes: usize) -> Result<UnsignedProgram, i32> {
    reject_unusable_program(longs, doubles, nodes)?;
    // SAFETY: checked non-null and nodes <= MAXIMUM_NODES; Java passes nodes * stride live values.
    let long_slice = unsafe { borrow_input(longs, nodes * UNSIGNED_LONGS_PER_NODE) };
    // SAFETY: as above, for the double stride; both slices are copied into owned nodes here.
    let double_slice = unsafe { borrow_input(doubles, nodes * UNSIGNED_DOUBLES_PER_NODE) };
    UnsignedProgram::decode(long_slice, double_slice, nodes).map_err(|_| STATUS_INVALID_PROGRAM)
}

fn decoded_program(longs: *const i64, doubles: *const f64, nodes: usize) -> Result<Program, i32> {
    reject_unusable_program(longs, doubles, nodes)?;
    // SAFETY: checked non-null and nodes <= MAXIMUM_NODES; Java passes nodes * stride live values.
    let long_slice = unsafe { borrow_input(longs, nodes * LONGS_PER_NODE) };
    // SAFETY: as above, for the double stride; both slices are copied into owned nodes here.
    let double_slice = unsafe { borrow_input(doubles, nodes * DOUBLES_PER_NODE) };
    Program::decode(long_slice, double_slice, nodes).map_err(|_| STATUS_INVALID_PROGRAM)
}

fn reject_unusable_program(longs: *const i64, doubles: *const f64, nodes: usize) -> Result<(), i32> {
    if longs.is_null() || doubles.is_null() {
        return Err(STATUS_NULL_POINTER);
    }
    if nodes == 0 || nodes > MAXIMUM_NODES { Err(STATUS_INVALID_PROGRAM) } else { Ok(()) }
}

type Buffers<'a> = (Vec<&'a [f64]>, &'a mut [f64]);

fn checked_buffers<'a>(inputs: &[*const f64], out: *mut f64, len: usize) -> Result<Buffers<'a>, i32> {
    reject_unusable_buffers(inputs, out, len)?;
    // SAFETY: `reject_unusable_buffers` checked every precondition of both borrows.
    let borrowed = unsafe { (inputs.iter().map(|input| borrow_input(*input, len)).collect(), borrow_output(out, len)) };
    Ok(borrowed)
}

/// # Safety
/// `pointer` is non-null, aligned, and points at `len` doubles that stay alive and unmodified
/// for the borrow; `len * 8` fits in `isize`.
unsafe fn borrow_input<'a, T>(pointer: *const T, len: usize) -> &'a [T] {
    unsafe { std::slice::from_raw_parts(pointer, len) }
}

/// # Safety
/// As `borrow_input`, and no other live reference overlaps the `len` doubles at `pointer`.
unsafe fn borrow_output<'a>(pointer: *mut f64, len: usize) -> &'a mut [f64] {
    unsafe { std::slice::from_raw_parts_mut(pointer, len) }
}

fn reject_unusable_buffers(inputs: &[*const f64], out: *mut f64, len: usize) -> Result<(), i32> {
    reject_null_pointers(inputs, out)?;
    let bytes = byte_length(len)?;
    reject_output_aliasing(inputs, out, bytes)
}

fn reject_null_pointers(inputs: &[*const f64], out: *mut f64) -> Result<(), i32> {
    let any_null = out.is_null() || inputs.iter().any(|input| input.is_null());
    if any_null { Err(STATUS_NULL_POINTER) } else { Ok(()) }
}

fn byte_length(len: usize) -> Result<usize, i32> {
    let bytes = len.checked_mul(DOUBLE_BYTES);
    bytes.filter(|bytes| *bytes <= isize::MAX as usize).ok_or(STATUS_TOO_LONG)
}

fn reject_output_aliasing(inputs: &[*const f64], out: *mut f64, bytes: usize) -> Result<(), i32> {
    let aliased = inputs.iter().any(|input| ranges_overlap(*input as usize, out as usize, bytes));
    if aliased { Err(STATUS_OUTPUT_ALIASES_INPUT) } else { Ok(()) }
}

fn ranges_overlap(first: usize, second: usize, bytes: usize) -> bool {
    bytes > 0 && first < second.saturating_add(bytes) && second < first.saturating_add(bytes)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn null_pointers_are_refused_before_any_read() {
        let mut out = [0.0; 2];
        let status = volmlib_noise_simplex_2d_fill(1, std::ptr::null(), [0.0; 2].as_ptr(), out.as_mut_ptr(), 2);
        assert_eq!(status, STATUS_NULL_POINTER);
    }

    #[test]
    fn output_that_overlaps_an_input_is_refused() {
        let mut shared = [0.5; 4];
        let pointer = shared.as_mut_ptr();
        let status = volmlib_noise_simplex_2d_fill(1, pointer, [0.0; 4].as_ptr(), pointer, 4);
        assert_eq!(status, STATUS_OUTPUT_ALIASES_INPUT);
    }

    #[test]
    fn lengths_whose_byte_size_overflows_are_refused() {
        let mut out = [0.0; 1];
        let input = [0.0; 1];
        let status = volmlib_noise_simplex_2d_fill(1, input.as_ptr(), input.as_ptr(), out.as_mut_ptr(), usize::MAX);
        assert_eq!(status, STATUS_TOO_LONG);
    }

    #[test]
    fn invalid_program_node_counts_are_refused_before_reading_buffers() {
        let (longs, doubles) = ([1_i64, 7, 1], [0.01, 1.0, 0.0, 0.0, 1.0, 1.0, 0.0]);
        let (input, mut out) = ([0.5], [0.0]);
        for nodes in [0, MAXIMUM_NODES + 1] {
            let status = volmlib_noise_program_fill_2d(longs.as_ptr(), doubles.as_ptr(), nodes, input.as_ptr(), input.as_ptr(), out.as_mut_ptr(), 1);
            assert_eq!(status, STATUS_INVALID_PROGRAM);
        }
    }

    #[test]
    fn program_with_bad_octaves_is_refused_and_output_is_untouched() {
        let (longs, doubles) = ([0_i64, 7, 0], [0.01, 1.0, 0.0, 0.0, 1.0, 1.0, 0.0]);
        let (input, mut out) = ([0.5], [9.0]);
        let status = volmlib_noise_program_fill_2d(longs.as_ptr(), doubles.as_ptr(), 1, input.as_ptr(), input.as_ptr(), out.as_mut_ptr(), 1);
        assert_eq!((status, out[0]), (STATUS_INVALID_PROGRAM, 9.0));
    }

    #[test]
    fn null_program_arrays_are_refused() {
        let (input, mut out) = ([0.5], [0.0]);
        let status = volmlib_noise_program_fill_3d(std::ptr::null(), std::ptr::null(), 1, input.as_ptr(), input.as_ptr(), input.as_ptr(), out.as_mut_ptr(), 1);
        assert_eq!(status, STATUS_NULL_POINTER);
    }

    #[test]
    fn adjacent_buffers_do_not_count_as_overlapping() {
        assert!(!ranges_overlap(0, 16, 16));
        assert!(ranges_overlap(0, 15, 16));
        assert!(!ranges_overlap(0, 0, 0));
    }

    #[test]
    fn fill_matches_point_sampling() {
        let (xs, ys) = ([0.1, -4.2, 9.9], [3.3, 0.0, -7.7]);
        let mut out = [0.0; 3];
        assert_eq!(volmlib_noise_simplex_2d_fill(3, xs.as_ptr(), ys.as_ptr(), out.as_mut_ptr(), 3), STATUS_OK);
        assert_eq!(out, [0, 1, 2].map(|i| simplex_2d(3, xs[i], ys[i])));
    }
}

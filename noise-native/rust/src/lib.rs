//! Bit-exact port of the simplex part of VolmLib's `FastNoiseDouble`.
//!
//! Every function keeps the Java evaluation order and 64-bit wrapping integer semantics, so a
//! seed produces the same world whether Java or Rust samples it.

pub mod batch;
mod ffi;
pub mod gradient;
pub mod hash;
pub mod program;
pub mod simplex2d;
pub mod simplex3d;
pub mod unsigned;

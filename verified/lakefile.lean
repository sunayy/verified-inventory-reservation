import Lake
open Lake DSL

package «inventory-reservation» where
  srcDir := "."
  buildDir := ".lake/build"

lean_lib Reservation where
  roots := #[`Reservation]
  precompileModules := true

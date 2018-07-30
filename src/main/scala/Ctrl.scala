package spmv

import chisel3._

class Ctrl(val w: Int = 32) extends Module {
	val io = new Bundle {
		val m = Input(UInt(w.W))
		val n = Input(UInt(w.W))
		val nnz = Input(UInt(w.W))
		val rowptr = Input(UInt(w.W))
		val colidx = Input(UInt(w.W))
		val weight = Input(UInt(w.W))
		val x = Input(UInt(w.W))
		val y = Input(UInt(w.W))
	}
}

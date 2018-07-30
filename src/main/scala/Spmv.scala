package sgemm

import chisel3._

object BLOCK {
	val N = 8
}

class Mac(val w: Int = 32) extends Module {
	val io = IO(new Bundle {
		val a = Input(SInt(w.W))
		val b = Input(SInt(w.W))
		val c = Input(SInt(w.W))
		val out = Output(SInt(w.W))
	})
	io.out := io.a * io.b + io.c
}

class Reducer(val w: Int = 32, val k: Int = BLOCK.N) extends Module {
	val io = IO(new Bundle {
		val row = Input(Vec(k, SInt(w.W)))
		val col = Input(Vec(k, SInt(w.W)))
		val res = Output(SInt(w.W))
	})
	val res = Wire(Vec(k, SInt(w.W)))
	res(0) := io.row(0) * io.col(0)
	for(i <- 1 until k) {
		val mac = Module(new Mac(w))
		res(i) := mac.io.out
		mac.io.a := io.row(i)
		mac.io.b := io.col(i)
		mac.io.c := res(i-1)
	}
	io.res := res(k-1)
}

class Spmv(val w: Int = 32, val numMul: Int = 8) extends Module {
	val io = IO(new Bundle {
		val start  = Input(Bool())
		val load   = Input(Bool())
		val rowptr = Input(UInt(w.W))
		val colidx = Input(UInt(w.W))
		val weight = Input(UInt(w.W))
		val nnz    = Input(UInt(w.W))
		val x      = Input(Vec(n, SInt(w.W)))
		val y_in   = Input(Vec(n, SInt(w.W)))
		val y_out  = Output(Vec(n, SInt(w.W)))
		val done   = Output(Bool())
	})

	val n = BLOCK.N
	val initValues = Seq.fill(n) { 0.U(w.W) }
	// a buffer to hold the partial sum of this column block
	val result = RegInit(VecInit(initValues))
	// a buffer to hold the input data block
	val x = RegInit(VecInit(initValues))

	when (io.load) {
		for(i <- 0 until n) {
			x(i) := io.x(i)
			result(i) := io.y_in(i)
		}
	}

	for(i <- 0 until n) {
		io.y_out(i) := result(i)
	}

	for(i <- 0 until m) {
		val start = rowptr(i)
		val end = rowptr(i+1)
		val len = end - start
		val red = Module(new Reducer(w,len))
		for(k <- 0 until l) {
			red.io.row(k) := io.weight(
			val j = io.colidx(start+k)
			red.io.col(k) := io.matB(k*n+j)
		}
		io.y(j) := red.io.res
	}

	io.done := false.B
	val regCompletedOps = RegInit(0.U(32.W))

	val sIdle :: sRunning :: sFinished :: Nil = Enum(3)
	val regState = RegInit(sIdle)

	switch(regState) {
		is(sIdle) {
			when(io.start) {
				regState := sRunning
				regCompletedOps := 0.U
			}
		}
		is(sRunning) {
			// count completed operations
			when (sched.io.compl.ready & sched.io.compl.valid) {
				regCompletedOps := regCompletedOps + 1.U
			}
			when (regCompletedOps === io.csc.nz) { regState := sFinished }
		}
		is(sFinished) {
			io.done := true.B
			when (!io.start) {regState := sIdle}
		}
	}
}

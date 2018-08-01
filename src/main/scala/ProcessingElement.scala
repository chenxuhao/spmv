package spmv

import chisel3._
import chisel3.util._

class Mac(val w: Int = 32) extends Module {
	val io = IO(new Bundle {
		val a = Input(UInt(w.W))
		val b = Input(UInt(w.W))
		val c = Input(UInt(w.W))
		val out = Output(UInt(w.W))
	})
	io.out := io.a * io.b + io.c
}

class ProcessingElement(val w: Int = 32, val n: Int = 8) extends Module {
	val io = IO(new Bundle {
		val valid  = Input(Bool())
		val colptr = Input(UInt(w.W))
		val rowidx = Input(UInt(w.W))
		val value  = Input(UInt(w.W))
//		val nnz    = Input(UInt(w.W))
		val x_in   = Input(UInt(w.W))
//		val y_in   = Input(UInt(w.W))
		val y_out  = Output(UInt(w.W))
		//val idx    = Output(UInt(w.W))
		//val done   = Output(Bool())
	})

	//val initValues = Seq.fill(n) { 0.U(w.W) }
	//val buffer = RegInit(VecInit(initValues))
	//val idx = RegInit(0.U(w.W))

	val data_in = Wire(UInt(w.W))
	val rd_addr = Wire(UInt(log2Ceil(n).W))
	val wr_addr = Wire(UInt(log2Ceil(n).W))
	val rd_en   = Wire(Bool())
	val wr_en   = Wire(Bool())

	io.y_out := 0.U
	data_in := 0.U
	rd_addr := 0.U
	wr_addr := 0.U
	rd_en := false.B
	wr_en := false.B
	
	// a simple dual-port RAM to hold the partial sum
	val buffer_mem = SyncReadMem(n, UInt(w.W))
	val data_out = buffer_mem.read(rd_addr, rd_en)

	val mac = Module(new Mac(w))
	mac.io.a := io.value
	mac.io.b := io.x_in
	mac.io.c := data_out
	data_in := mac.io.out

	//io.idx  := idx
	//io.done := false.B
	val s_idle :: s_read :: s_write :: Nil = Enum(3)
	val state = RegInit(s_idle)

	when (wr_en) {
		buffer_mem.write(wr_addr, data_in)
	}

	switch(state) {
		is(s_idle) {
			when(io.valid) {
				state := s_read
			}
		}
		is(s_read) {
			//idx := io.rowidx
			rd_addr := io.rowidx % n.asUInt()
			rd_en := io.valid
			state := s_write
		}
		is(s_write) {
			wr_addr := io.rowidx % n.asUInt()
			wr_en := io.valid
			state := s_idle
		}
	}
}

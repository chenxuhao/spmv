package spmv

import chisel3._
import chisel3.util._
import freechips.rocketchip.rocket._
import freechips.rocketchip.config._

// read the CSC data structures and input(x)/output(y) vectors from host memory
class Controller(val w: Int = 32, val n: Int = 8)(implicit p: Parameters) extends Module {
	val block_size = BLOCK.N
	val io = IO(new Bundle {
		val rocc_req_val = Input(Bool())
		val rocc_req_rdy = Output(Bool())
		val rocc_funct   = Input(Bits(2.W))
		val rocc_rs1     = Input(Bits(64.W))
		val rocc_rs2     = Input(Bits(64.W))
		val rocc_rd      = Input(Bits(5.W))
		val rocc_fire    = Input(Bool())
		val resp_data    = Output(UInt(32.W))
		val resp_rd      = Output(Bits(5.W))
		val resp_valid   = Output(Bool())

		val dmem_req_val = Output(Bool())
		val dmem_req_rdy = Input(Bool())
		val dmem_req_tag = Output(UInt(7.W))
		val dmem_req_cmd = Output(UInt(M_SZ.W))
		val dmem_req_typ = Output(UInt(MT_SZ.W))
		val dmem_req_addr = Output(UInt(32.W))

		val dmem_resp_val = Input(Bool())
		val dmem_resp_tag = Input(UInt(7.W))
		val dmem_resp_data = Input(UInt(w.W))

		val busy   = Output(Bool())
		val valid  = Output(Vec(n, Bool()))
		val colptr = Output(Vec(n, UInt(w.W)))
		val rowidx = Output(Vec(n, UInt(w.W)))
		val value  = Output(Vec(n, UInt(w.W)))
		val x_out  = Output(Vec(n, UInt(w.W)))
	})

	val nrow = RegInit(0.U(32.W))
	val ncol = RegInit(0.U(32.W))
	val nnz  = RegInit(0.U(32.W))
	val busy = RegInit(false.B)

	// addresses (64-bit)
	val x_addr = RegInit(0.U(64.W))
	val y_addr = RegInit(0.U(64.W))
	val z_addr = RegInit(0.U(64.W))
	val colptr = RegInit(0.U(64.W))
	val rowidx = RegInit(0.U(64.W))
	val values = RegInit(0.U(64.W))

	val x_idx = RegInit(0.U(64.W))
	val col_idx = RegInit(0.U(64.W))
	val row_idx = RegInit(0.U(64.W))
	val val_idx = RegInit(0.U(64.W))

	// initialize output signals
	io.busy         := busy
	io.dmem_req_val := false.B
	io.dmem_req_tag := 0.U(7.W)
	io.dmem_req_cmd := M_XRD
	io.dmem_req_typ := MT_D
	io.dmem_req_addr:= 0.U(32.W)
	io.rocc_req_rdy := !busy
	io.resp_rd      := io.rocc_rd
	io.resp_valid   := io.rocc_req_val

	for (i <- 0 until n) {
		io.valid(i)  := false.B
		io.colptr(i) := 0.U
		io.rowidx(i) := 0.U
		io.value(i) := 0.U
		io.x_out(i) := 0.U
	}

	// for debug
	when (io.rocc_rs2 === 0.U) {
		io.resp_data := x_addr
	} .elsewhen (io.rocc_rs2 === 1.U) {
		io.resp_data := y_addr
	} .elsewhen (io.rocc_rs2 === 2.U) {
		io.resp_data := colptr
	} .elsewhen (io.rocc_rs2 === 3.U) {
		io.resp_data := rowidx
	} .elsewhen (io.rocc_rs2 === 4.U) {
		io.resp_data := values
	} .otherwise {
		io.resp_data := nnz
	}

	// decode the rocc instruction
	when (io.rocc_req_val && !busy) {
		when (io.rocc_funct === 0.U) {
			io.busy := true.B
			io.rocc_req_rdy := true.B
			x_addr := io.rocc_rs1
			y_addr := io.rocc_rs2
		} .elsewhen (io.rocc_funct === 1.U) {
			io.busy := true.B
			io.rocc_req_rdy := true.B
			colptr := io.rocc_rs1
			rowidx := io.rocc_rs2
		} .elsewhen (io.rocc_funct === 2.U) {
			io.busy := true.B
			io.rocc_req_rdy := true.B
			//nrow := io.rocc_rs1
			//ncol := io.rocc_rs2
		} .elsewhen (io.rocc_funct === 3.U) {
			io.busy := true.B
			io.rocc_req_rdy := true.B
			busy := true.B
			values := io.rocc_rs1
			nnz := io.rocc_rs2
		}
	}

	val m_idle :: m_read_x :: m_wait_x :: m_read_colptr :: m_wait_colptr :: m_read_rowidx :: m_wait_rowidx :: m_read_value :: m_wait_value :: m_launch :: Nil = Enum(10)
	val mem_s = RegInit(m_idle)
	val read  = RegInit(0.U(32.W))
	val xindex = RegInit(0.U(5.W))
	val buffer_valid = RegInit(false.B)
	val buffer_count = RegInit(0.U(5.W))
	val col_count = RegInit(0.U(5.W))
	val row_count = RegInit(0.U(5.W))
	val initValues = Seq.fill(block_size) { 0.U(w.W) }
	val buffer = RegInit(VecInit(initValues))
	val writes_done = RegInit(VecInit(Seq.fill(block_size){false.B}))
	val aindex = RegInit(0.U(log2Ceil(block_size).W)) // absorb counter
	val cindex = RegInit(0.U(log2Ceil(block_size).W))
	val col_start = RegInit(0.U(w.W))
	val col_end = RegInit(0.U(w.W))
	x_idx := x_addr
	col_idx := colptr
	row_idx := rowidx
	val_idx := values
	for (i <- 0 until n) {
		io.rowidx(i) := RegNext(aindex)
		io.x_out(i) := buffer(io.aindex)
	}

	switch(mem_s) {
		is(m_idle) {
			val canRead = busy && read < nnz && !buffer_valid && buffer_count === 0.U
			when (canRead) {
				// start reading data
				xindex := 0.U
				mem_s := m_read_x
			} .otherwise {
				mem_s := m_idle
			}
		}
		is(m_read_x) {
			//only read if we aren't writing
			when (state =/= s_write) {
				//dmem signals
				io.dmem_req_val := read < nnz && xindex < block_size.U
				io.dmem_req_addr:= x_addr + (xindex << 2.U) // read 1 word each time, 1 word = 4 bytes, so left shift 2 (i.e. times 4)
				io.dmem_req_tag := xindex
				io.dmem_req_cmd := M_XRD
				io.dmem_req_typ := MT_D

				// read data if ready and valid
				when(io.dmem_req_rdy && io.dmem_req_val) {
					xindex := xindex + 1.U // read 1 word each time
					read := read + 4.U // read 4 bytes each time
					mem_s := m_wait_x // wait until reading done
				} .otherwise {
					mem_s := m_read_x
				}
			}
		}
		is(m_wait_x) {
			when (io.dmem_resp_val) {
				// put the recieved data into buffer
				buffer(xindex - 1.U) := io.dmem_resp_data
			}
			buffer_count := buffer_count + 1.U

			// next state
			// the buffer is not full
			when (xindex < block_size.U) {
				when (read < nnz) {
					// continue reading
					mem_s := m_read_x
				} .otherwise {
					buffer_valid := false.B
					mem_s := m_read_colptr
				}
			} .otherwise {
				// vector not done yet, but buffer is full
				x_addr := x_addr + (block_size << 2).U
				buffer_valid := false.B
				mem_s := m_read_colptr
			}
		}
		is(m_read_colptr) {
			when (state =/= s_write) {
				// done reading x, start reading colptr
				io.dmem_req_val := cindex < block_size.U
				io.dmem_req_addr:= colptr + (cindex << 2.U)
				io.dmem_req_tag := cindex
				io.dmem_req_cmd := M_XRD
				io.dmem_req_typ := MT_D

				// read data if ready and valid
				when(io.dmem_req_rdy && io.dmem_req_val) {
					cindex := cindex + 1.U // read 1 word each time
					mem_s := m_wait_colptr // wait until reading done
				} .otherwise {
					mem_s := m_read_colptr
				}
			}
		}
		is(m_wait_colptr) {
			when (io.dmem_resp_val) {
				col_start := io.dmem_resp_data
			}
		}
		is(m_launch) {
			buffer_valid := true.B
			//move to idle when we know this data was processed
			when(aindex >= (block_size-1).U) {
				mem_s := m_idle
			} .otherwise {
				mem_s := m_launch
			}
		}
	}
}

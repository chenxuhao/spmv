package spmv

import chisel3._
import chisel3.util._
import freechips.rocketchip.rocket._
import freechips.rocketchip.config._
import freechips.rocketchip.tile._

case object WidthP extends Field[Int]
case object NumPEs extends Field[Int]
//case object FastMem extends Field[Boolean]
//case object BufferSram extends Field[Boolean]

class SpmvAccel(opcodes: OpcodeSet)(implicit p: Parameters) extends LazyRoCC(opcodes) {
	override lazy val module = new SpmvAccelModuleImp(this)
}

class SpmvAccelModuleImp(outer: SpmvAccel) extends LazyRoCCModuleImp(outer) with HasCoreParameters {
	val w = outer.p(WidthP)
	val n = outer.p(NumPEs)

	// control
	val ctrl = Module(new Controller(w, n)(outer.p))

	ctrl.io.busy         <> io.busy
	ctrl.io.rocc_rd      <> io.cmd.bits.inst.rd
	ctrl.io.rocc_rs1     <> io.cmd.bits.rs1
	ctrl.io.rocc_rs2     <> io.cmd.bits.rs2
	ctrl.io.rocc_funct   <> io.cmd.bits.inst.funct
	ctrl.io.rocc_req_val <> io.cmd.valid
	ctrl.io.rocc_req_rdy <> io.cmd.ready
	ctrl.io.resp_rd      <> io.resp.bits.rd
	ctrl.io.resp_data    <> io.resp.bits.data
	ctrl.io.resp_valid   <> io.resp.valid
	ctrl.io.rocc_fire    := io.cmd.fire()

	ctrl.io.dmem_req_val <> io.mem.req.valid
	ctrl.io.dmem_req_rdy <> io.mem.req.ready
	ctrl.io.dmem_req_tag <> io.mem.req.bits.tag
	ctrl.io.dmem_req_cmd <> io.mem.req.bits.cmd
	ctrl.io.dmem_req_typ <> io.mem.req.bits.typ
	ctrl.io.dmem_req_addr<> io.mem.req.bits.addr

	ctrl.io.dmem_resp_val  <> io.mem.resp.valid
	ctrl.io.dmem_resp_tag  <> io.mem.resp.bits.tag
	ctrl.io.dmem_resp_data := io.mem.resp.bits.data

	// processing elements
	//val pe_array = Vec.fill(n){ Module(new ProcessingElement(w)).io }
	val pe_array = VecInit(Seq.fill(n)(Module(new ProcessingElement(w)).io))

	for (i <- 0 until n) {
		pe_array(i).colptr <> ctrl.io.colptr(i)
		pe_array(i).rowidx <> ctrl.io.rowidx(i)
		pe_array(i).value  <> ctrl.io.value(i)
		pe_array(i).x_in   <> ctrl.io.x_out(i)
		pe_array(i).valid  <> ctrl.io.valid(i)
	}
	
	// write output data back to the memory
	//val idx = pe_array(i).idx
	//io.mem.req.bits.data := pe_array(i).y_out
	io.interrupt  := false.B
	io.resp.valid := false.B
}

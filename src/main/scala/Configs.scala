//see LICENSE for license
package freechips.rocketchip.system

import Chisel._
import freechips.rocketchip.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.system._
import freechips.rocketchip.tile._
import freechips.rocketchip.diplomacy._
import spmv._

class WithSpmvAccel extends Config((site, here, up) => {
	case WidthP => 32
	case NumPEs => 8
	case BuildRoCC => List(
		(p: Parameters) => {
			val spmv = LazyModule(new SpmvAccel(OpcodeSet.custom0)(p))
			spmv
		}
	)
})

class SpmvAccelConfig extends Config(new WithSpmvAccel ++ new DefaultConfig)

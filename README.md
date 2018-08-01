Spmv Accelerator
=============
Spmv is a Chisel3 implementation of sparse matrix vector multiplication accelerator. 

Usage:

    $ sbt

    > test:runMain spmv.SpmvMain

You can build the emulator using

    cd ../emulator && make ROCKETCHIP_ADDONS=spmv CONFIG=SpmvAccelConfig

You can then test it using the emulator

    make run-asm-tests

You can emulate the software implementation of spmv by running

    ./emulator-freechips.rocketchip.system-SpmvAccelConfig pk ../spmv/tests/spmv-sw.rv

or

    ./emulator-freechips.rocketchip.system-SpmvAccelConfig pk ../spmv/tests/spmv-sw-bm.rv

You can emulate the accelerated spmv by running

    ./emulator-freechips.rocketchip.system-SpmvAccelConfig pk ../spmv/tests/spmv-rocc-bm.rv

or 

    ./emulator-freechips.rocketchip.system-SpmvAccelConfig pk ../spmv/tests/spmv-rocc.rv

The -bm versions of the code omit the print statements and will complete faster.

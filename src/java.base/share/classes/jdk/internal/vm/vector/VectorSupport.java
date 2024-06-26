/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.internal.vm.vector;

import jdk.internal.HotSpotIntrinsicCandidate;
import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.*;

public class VectorSupport {
    static {
        registerNatives();
    }

    private static final Unsafe U = Unsafe.getUnsafe();

    // Unary
    public static final int VECTOR_OP_ABS  = 0;
    public static final int VECTOR_OP_NEG  = 1;
    public static final int VECTOR_OP_SQRT = 2;

    // Binary
    public static final int VECTOR_OP_ADD  = 4;
    public static final int VECTOR_OP_SUB  = 5;
    public static final int VECTOR_OP_MUL  = 6;
    public static final int VECTOR_OP_DIV  = 7;
    public static final int VECTOR_OP_MIN  = 8;
    public static final int VECTOR_OP_MAX  = 9;

    public static final int VECTOR_OP_AND  = 10;
    public static final int VECTOR_OP_OR   = 11;
    public static final int VECTOR_OP_XOR  = 12;

    // Ternary
    public static final int VECTOR_OP_FMA  = 13;

    // Broadcast int
    public static final int VECTOR_OP_LSHIFT  = 14;
    public static final int VECTOR_OP_RSHIFT  = 15;
    public static final int VECTOR_OP_URSHIFT = 16;

    public static final int VECTOR_OP_CAST        = 17;
    public static final int VECTOR_OP_REINTERPRET = 18;

    // enum BoolTest
    public static final int BT_eq = 0;
    public static final int BT_ne = 4;
    public static final int BT_le = 5;
    public static final int BT_ge = 7;
    public static final int BT_lt = 3;
    public static final int BT_gt = 1;
    public static final int BT_overflow = 2;
    public static final int BT_no_overflow = 6;

    // BasicType codes, for primitives only:
    public static final int
        T_FLOAT   = 6,
        T_DOUBLE  = 7,
        T_BYTE    = 8,
        T_SHORT   = 9,
        T_INT     = 10,
        T_LONG    = 11;

    /* ============================================================================ */

    public static class VectorSpecies<E> {}

    public static class VectorPayload {
        private final Object payload; // array of primitives

        public VectorPayload(Object payload) {
            this.payload = payload;
        }

        protected final Object getPayload() {
            return VectorSupport.maybeRebox(this).payload;
        }
    }

    public static class Vector<E> extends VectorPayload {
        public Vector(Object payload) {
            super(payload);
        }
    }

    public static class VectorShuffle<E> extends VectorPayload {
        public VectorShuffle(Object payload) {
            super(payload);
        }
    }
    public static class VectorMask<E> extends VectorPayload {
        public VectorMask(Object payload) {
            super(payload);
        }
    }

    /* ============================================================================ */
    public interface BroadcastOperation<VM, E, S extends VectorSpecies<E>> {
        VM broadcast(long l, S s);
    }

    @HotSpotIntrinsicCandidate
    public static
    <VM, E, S extends VectorSpecies<E>>
    VM broadcastCoerced(Class<? extends VM> vmClass, Class<E> E, int length,
                                  long bits, S s,
                                  BroadcastOperation<VM, E, S> defaultImpl) {
        assert isNonCapturingLambda(defaultImpl) : defaultImpl;
        return defaultImpl.broadcast(bits, s);
    }

    /* ============================================================================ */
    public interface ShuffleIotaOperation<E, S extends VectorSpecies<E>> {
        VectorShuffle<E> apply(int length, int start, int step, S s);
    }

    @HotSpotIntrinsicCandidate
    public static
    <E, S extends VectorSpecies<E>>
    VectorShuffle<E> shuffleIota(Class<?> E, Class<?> ShuffleClass, S s, int length,
                     int start, int step, int wrap, ShuffleIotaOperation<E, S> defaultImpl) {
       assert isNonCapturingLambda(defaultImpl) : defaultImpl;
       return defaultImpl.apply(length, start, step, s);
    }

    public interface ShuffleToVectorOperation<VM, Sh, E> {
       VM apply(Sh s);
    }

    @HotSpotIntrinsicCandidate
    public static
    <VM ,Sh extends VectorShuffle<E>, E>
    VM shuffleToVector(Class<?> VM, Class<?>E , Class<?> ShuffleClass, Sh s, int length,
                       ShuffleToVectorOperation<VM,Sh,E> defaultImpl) {
      assert isNonCapturingLambda(defaultImpl) : defaultImpl;
      return defaultImpl.apply(s);
    }

    /* ============================================================================ */
    public interface IndexOperation<V extends Vector<E>, E, S extends VectorSpecies<E>> {
        V index(V v, int step, S s);
    }

    //FIXME @HotSpotIntrinsicCandidate
    public static
    <V extends Vector<E>, E, S extends VectorSpecies<E>>
    V indexVector(Class<? extends V> vClass, Class<E> E, int length,
                  V v, int step, S s,
                  IndexOperation<V, E, S> defaultImpl) {
        assert isNonCapturingLambda(defaultImpl) : defaultImpl;
        return defaultImpl.index(v, step, s);
    }

    /* ============================================================================ */

    @HotSpotIntrinsicCandidate
    public static
    <V extends Vector<?>>
    long reductionCoerced(int oprId, Class<?> vectorClass, Class<?> elementType, int length,
                          V v,
                          Function<V,Long> defaultImpl) {
        assert isNonCapturingLambda(defaultImpl) : defaultImpl;
        return defaultImpl.apply(v);
    }

    /* ============================================================================ */

    public interface VecExtractOp<V> {
        long apply(V v1, int idx);
    }

    @HotSpotIntrinsicCandidate
    public static
    <V extends Vector<?>>
    long extract(Class<?> vectorClass, Class<?> elementType, int vlen,
                 V vec, int ix,
                 VecExtractOp<V> defaultImpl) {
        assert isNonCapturingLambda(defaultImpl) : defaultImpl;
        return defaultImpl.apply(vec, ix);
    }

    /* ============================================================================ */

    public interface VecInsertOp<V> {
        V apply(V v1, int idx, long val);
    }

    @HotSpotIntrinsicCandidate
    public static
    <V extends Vector<?>>
    V insert(Class<? extends V> vectorClass, Class<?> elementType, int vlen,
             V vec, int ix, long val,
             VecInsertOp<V> defaultImpl) {
        assert isNonCapturingLambda(defaultImpl) : defaultImpl;
        return defaultImpl.apply(vec, ix, val);
    }

    /* ============================================================================ */

    @HotSpotIntrinsicCandidate
    public static
    <VM>
    VM unaryOp(int oprId, Class<? extends VM> vmClass, Class<?> elementType, int length,
               VM vm,
               Function<VM, VM> defaultImpl) {
        assert isNonCapturingLambda(defaultImpl) : defaultImpl;
        return defaultImpl.apply(vm);
    }

    /* ============================================================================ */

    @HotSpotIntrinsicCandidate
    public static
    <VM>
    VM binaryOp(int oprId, Class<? extends VM> vmClass, Class<?> elementType, int length,
                VM vm1, VM vm2,
                BiFunction<VM, VM, VM> defaultImpl) {
        assert isNonCapturingLambda(defaultImpl) : defaultImpl;
        return defaultImpl.apply(vm1, vm2);
    }

    /* ============================================================================ */

    public interface TernaryOperation<V> {
        V apply(V v1, V v2, V v3);
    }

    @HotSpotIntrinsicCandidate
    public static
    <VM>
    VM ternaryOp(int oprId, Class<? extends VM> vmClass, Class<?> elementType, int length,
                 VM vm1, VM vm2, VM vm3,
                 TernaryOperation<VM> defaultImpl) {
        assert isNonCapturingLambda(defaultImpl) : defaultImpl;
        return defaultImpl.apply(vm1, vm2, vm3);
    }

    /* ============================================================================ */

    // Memory operations

    public interface LoadOperation<C, V, E, S extends VectorSpecies<E>> {
        V load(C container, int index, S s);
    }

    @HotSpotIntrinsicCandidate
    public static
    <C, VM, E, S extends VectorSpecies<E>>
    VM load(Class<? extends VM> vmClass, Class<E> E, int length,
           Object base, long offset,    // Unsafe addressing
           C container, int index, S s,     // Arguments for default implementation
           LoadOperation<C, VM, E, S> defaultImpl) {
        assert isNonCapturingLambda(defaultImpl) : defaultImpl;
        return defaultImpl.load(container, index, s);
    }

    /* ============================================================================ */

    public interface LoadVectorOperationWithMap<C, V extends Vector<?>, E, S extends VectorSpecies<E>> {
        V loadWithMap(C container, int index, int[] indexMap, int indexM, S s);
    }

    @HotSpotIntrinsicCandidate
    public static
    <C, V extends Vector<?>, W extends Vector<Integer>, E, S extends VectorSpecies<E>>
    V loadWithMap(Class<?> vectorClass, Class<E> E, int length, Class<?> vectorIndexClass,
                  Object base, long offset, // Unsafe addressing
                  W index_vector,
                  C container, int index, int[] indexMap, int indexM, S s, // Arguments for default implementation
                  LoadVectorOperationWithMap<C, V, E, S> defaultImpl) {
        assert isNonCapturingLambda(defaultImpl) : defaultImpl;
        return defaultImpl.loadWithMap(container, index, indexMap, indexM, s);
    }

    /* ============================================================================ */

    public interface StoreVectorOperation<C, V extends Vector<?>> {
        void store(C container, int index, V v);
    }

    @HotSpotIntrinsicCandidate
    public static
    <C, V extends Vector<?>>
    void store(Class<?> vectorClass, Class<?> elementType, int length,
               Object base, long offset,    // Unsafe addressing
               V v,
               C container, int index,      // Arguments for default implementation
               StoreVectorOperation<C, V> defaultImpl) {
        assert isNonCapturingLambda(defaultImpl) : defaultImpl;
        defaultImpl.store(container, index, v);
    }

    /* ============================================================================ */

    public interface StoreVectorOperationWithMap<C, V extends Vector<?>> {
        void storeWithMap(C container, int index, V v, int[] indexMap, int indexM);
    }

    @HotSpotIntrinsicCandidate
    public static
    <C, V extends Vector<?>, W extends Vector<Integer>>
    void storeWithMap(Class<?> vectorClass, Class<?> elementType, int length, Class<?> vectorIndexClass,
                      Object base, long offset,    // Unsafe addressing
                      W index_vector, V v,
                      C container, int index, int[] indexMap, int indexM, // Arguments for default implementation
                      StoreVectorOperationWithMap<C, V> defaultImpl) {
        assert isNonCapturingLambda(defaultImpl) : defaultImpl;
        defaultImpl.storeWithMap(container, index, v, indexMap, indexM);
    }

    /* ============================================================================ */

    @HotSpotIntrinsicCandidate
    public static
    <VM>
    boolean test(int cond, Class<?> vmClass, Class<?> elementType, int length,
                 VM vm1, VM vm2,
                 BiFunction<VM, VM, Boolean> defaultImpl) {
        assert isNonCapturingLambda(defaultImpl) : defaultImpl;
        return defaultImpl.apply(vm1, vm2);
    }

    /* ============================================================================ */

    public interface VectorCompareOp<V,M> {
        M apply(int cond, V v1, V v2);
    }

    @HotSpotIntrinsicCandidate
    public static <V extends Vector<E>,
                   M extends VectorMask<E>,
                   E>
    M compare(int cond, Class<? extends V> vectorClass, Class<M> maskClass, Class<?> elementType, int length,
              V v1, V v2,
              VectorCompareOp<V,M> defaultImpl) {
        assert isNonCapturingLambda(defaultImpl) : defaultImpl;
        return defaultImpl.apply(cond, v1, v2);
    }

    /* ============================================================================ */

    public interface VectorRearrangeOp<V extends Vector<E>,
            Sh extends VectorShuffle<E>,
            E> {
        V apply(V v1, Sh shuffle);
    }

    @HotSpotIntrinsicCandidate
    public static
    <V extends Vector<E>,
            Sh extends VectorShuffle<E>,
            E>
    V rearrangeOp(Class<? extends V> vectorClass, Class<Sh> shuffleClass, Class<?> elementType, int vlen,
                  V v1, Sh sh,
                  VectorRearrangeOp<V,Sh, E> defaultImpl) {
        assert isNonCapturingLambda(defaultImpl) : defaultImpl;
        return defaultImpl.apply(v1, sh);
    }

    /* ============================================================================ */

    public interface VectorBlendOp<V extends Vector<E>,
            M extends VectorMask<E>,
            E> {
        V apply(V v1, V v2, M mask);
    }

    @HotSpotIntrinsicCandidate
    public static
    <V extends Vector<E>,
     M extends VectorMask<E>,
     E>
    V blend(Class<? extends V> vectorClass, Class<M> maskClass, Class<?> elementType, int length,
            V v1, V v2, M m,
            VectorBlendOp<V,M, E> defaultImpl) {
        assert isNonCapturingLambda(defaultImpl) : defaultImpl;
        return defaultImpl.apply(v1, v2, m);
    }

    /* ============================================================================ */

    public interface VectorBroadcastIntOp<V extends Vector<?>> {
        V apply(V v, int n);
    }

    @HotSpotIntrinsicCandidate
    public static
    <V extends Vector<?>>
    V broadcastInt(int opr, Class<? extends V> vectorClass, Class<?> elementType, int length,
                   V v, int n,
                   VectorBroadcastIntOp<V> defaultImpl) {
        assert isNonCapturingLambda(defaultImpl) : defaultImpl;
        return defaultImpl.apply(v, n);
    }

    /* ============================================================================ */

    public interface VectorConvertOp<VOUT, VIN, S> {
        VOUT apply(VIN v, S species);
    }

    // Users of this intrinsic assume that it respects
    // REGISTER_ENDIAN, which is currently ByteOrder.LITTLE_ENDIAN.
    // See javadoc for REGISTER_ENDIAN.

    @HotSpotIntrinsicCandidate
    public static <VOUT extends VectorPayload,
                    VIN extends VectorPayload,
                      S extends VectorSpecies<?>>
    VOUT convert(int oprId,
              Class<?> fromVectorClass, Class<?> fromElementType, int fromVLen,
              Class<?>   toVectorClass, Class<?>   toElementType, int   toVLen,
              VIN v, S s,
              VectorConvertOp<VOUT, VIN, S> defaultImpl) {
        assert isNonCapturingLambda(defaultImpl) : defaultImpl;
        return defaultImpl.apply(v, s);
    }

    /* ============================================================================ */

    @HotSpotIntrinsicCandidate
    public static <V> V maybeRebox(V v) {
        // The fence is added here to avoid memory aliasing problems in C2 between scalar & vector accesses.
        // TODO: move the fence generation into C2. Generate only when reboxing is taking place.
        U.loadFence();
        return v;
    }

    /* ============================================================================ */

    // query the JVM's supported vector sizes and types
    public static native int getMaxLaneCount(Class<?> etype);

    /* ============================================================================ */

    public static boolean isNonCapturingLambda(Object o) {
        return o.getClass().getDeclaredFields().length == 0;
    }

    /* ============================================================================ */

    private static native int registerNatives();
}

package com.mciot.core.api;

import java.util.Objects;

/**
 * 跨版本世界坐标封装（避免直接引用不同版本 Minecraft 的 BlockPos 与 ResourceLocation）
 */
public final class BlockPosKey {
    private final String dimension;
    private final int x;
    private final int y;
    private final int z;

    public BlockPosKey(String dimension, int x, int y, int z) {
        this.dimension = dimension == null ? "minecraft:overworld" : dimension;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public String dimension() { return dimension; }
    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlockPosKey that)) return false;
        return x == that.x && y == that.y && z == that.z && Objects.equals(dimension, that.dimension);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dimension, x, y, z);
    }

    @Override
    public String toString() {
        return dimension + "@(" + x + "," + y + "," + z + ")";
    }
}

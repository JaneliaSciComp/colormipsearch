package org.janelia.colormipsearch.image.type;

import java.math.BigInteger;

import net.imglib2.img.NativeImg;
import net.imglib2.img.basictypeaccess.IntAccess;
import net.imglib2.img.basictypeaccess.array.IntArray;
import net.imglib2.type.Index;
import net.imglib2.type.NativeType;
import net.imglib2.type.NativeTypeFactory;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.AbstractIntegerType;
import net.imglib2.util.Fraction;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class IntRGBPixelType extends AbstractIntegerType<IntRGBPixelType> implements RGBPixelType<IntRGBPixelType>, NativeType<IntRGBPixelType> {

    private static final NativeTypeFactory<IntRGBPixelType, IntArray> TYPE_FACTORY = NativeTypeFactory.INT( IntRGBPixelType::new );
    private final NativeImg<?, ? extends IntAccess> imgContainer;
    private IntAccess valueAccess;
    private final Index i;

    public IntRGBPixelType() {
        this(0);
    }

    public IntRGBPixelType(int v) {
        imgContainer = null;
        valueAccess = new IntArray(1);
        i = new Index();
        setInt(v);
    }

    public IntRGBPixelType(NativeImg<?, ? extends IntAccess> imgContainer) {
        i = new Index();
        this.imgContainer = imgContainer;
    }
    @Override
    public Fraction getEntitiesPerPixel() {
        return new Fraction(3, 1);
    }

    @Override
    public IntRGBPixelType duplicateTypeOnSameNativeImg() {
        return new IntRGBPixelType(imgContainer);
    }

    @Override
    public NativeTypeFactory<IntRGBPixelType, IntArray> getNativeTypeFactory() {
        return TYPE_FACTORY;
    }

    @Override
    public void updateContainer(Object c) {
        valueAccess = imgContainer.update(c);
    }

    @Override
    public Index index() {
        return i;
    }

    @Override
    public IntRGBPixelType createVariable() {
        return new IntRGBPixelType();
    }

    @Override
    public IntRGBPixelType copy() {
        return new IntRGBPixelType(getInteger());
    }

    @Override
    public void set(IntRGBPixelType c) {
        setInt(c.getInt());
    }

    @Override
    public boolean valueEquals(IntRGBPixelType argPixelType) {
        return getInt() == argPixelType.getInt();
    }

    @Override
    public int getRed() {
        return ARGBType.red(getInt());
    }

    @Override
    public int getGreen() {
        return ARGBType.green(getInt());
    }

    @Override
    public int getBlue() {
        return ARGBType.blue(getInt());
    }

    @Override
    public IntRGBPixelType createFromRGB(int r, int g, int b) {
        return new IntRGBPixelType().setInt(ARGBType.rgba(r, g, b, 0xff));
    }

    @Override
    public void setFromRGB(int r, int g, int b) {
        setInt(ARGBType.rgba(r, g, b, 0xff));
    }

    private int getInt() {
        return valueAccess.getValue(i.get());
    }

    private IntRGBPixelType setInt(int v) {
        int vi = i.get();
        valueAccess.setValue(vi, v);
        return this;
    }

    @Override
    public int getInteger() {
        return getInt();
    }

    @Override
    public long getIntegerLong() {
        return getInteger();
    }

    @Override
    public BigInteger getBigInteger() {
        return BigInteger.valueOf(getInteger());
    }

    @Override
    public void setInteger(int f) {
        setInt(f);
    }

    @Override
    public void setInteger(long f) {
        setInteger((int) f);
    }

    @Override
    public void setBigInteger(BigInteger b) {
        setInteger(b.intValue());
    }

    @Override
    public double getMaxValue() {
        return Integer.MAX_VALUE;
    }

    @Override
    public double getMinValue() {
        return Integer.MIN_VALUE;
    }

    @Override
    public int getBitsPerPixel() {
        return 32;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        IntRGBPixelType that = (IntRGBPixelType) o;

        return this.getInt() == that.getInt();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(getInt())
                .toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("r", getRed())
                .append("g", getGreen())
                .append("b", getBlue())
                .toString();
    }
}

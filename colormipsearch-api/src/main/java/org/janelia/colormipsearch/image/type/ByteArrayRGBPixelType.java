package org.janelia.colormipsearch.image.type;

import java.math.BigInteger;

import net.imglib2.img.NativeImg;
import net.imglib2.img.basictypeaccess.array.ByteArray;
import net.imglib2.type.Index;
import net.imglib2.type.NativeType;
import net.imglib2.type.NativeTypeFactory;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.AbstractIntegerType;
import net.imglib2.util.Fraction;

public class ByteArrayRGBPixelType extends AbstractIntegerType<ByteArrayRGBPixelType>
        implements RGBPixelType<ByteArrayRGBPixelType>, NativeType<ByteArrayRGBPixelType> {

    private static final NativeTypeFactory<ByteArrayRGBPixelType, ByteArray> TYPE_FACTORY = NativeTypeFactory.BYTE( ByteArrayRGBPixelType::new );
    private final NativeImg<?, ? extends ByteArray> imgContainer;
    private ByteArray valueAccess;
    private final Index i;

    public ByteArrayRGBPixelType() {
        this(0, 0, 0);
    }

    public ByteArrayRGBPixelType(int r, int g, int b) {
        imgContainer = null;
        valueAccess = new ByteArray(3);
        i = new Index();
        setValue(r, g, b);
    }

    public ByteArrayRGBPixelType(NativeImg<?, ? extends ByteArray> imgContainer) {
        i = new Index();
        this.imgContainer = imgContainer;
    }

    @Override
    public Fraction getEntitiesPerPixel() {
        return new Fraction(1, 3);
    }

    @Override
    public ByteArrayRGBPixelType duplicateTypeOnSameNativeImg() {
        return new ByteArrayRGBPixelType(imgContainer);
    }

    @Override
    public NativeTypeFactory<ByteArrayRGBPixelType, ByteArray> getNativeTypeFactory() {
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
    public ByteArrayRGBPixelType createVariable() {
        return new ByteArrayRGBPixelType(0, 0, 0);
    }

    @Override
    public ByteArrayRGBPixelType copy() {
        return new ByteArrayRGBPixelType(getRed(), getGreen(), getBlue());
    }

    @Override
    public void set(ByteArrayRGBPixelType c) {
        setValue(c.getRed(), c.getGreen(), c.getBlue());
    }

    @Override
    public boolean valueEquals(ByteArrayRGBPixelType argPixelType) {
        return getRed() == argPixelType.getRed() &&
                getGreen() == argPixelType.getGreen() &&
                getBlue() == argPixelType.getBlue();
    }

    @Override
    public int getRed() {
        return getValue(0);
    }

    @Override
    public int getGreen() {
        return getValue(1);
    }

    @Override
    public int getBlue() {
        return getValue(2);
    }

    @Override
    public ByteArrayRGBPixelType fromARGBType(ARGBType argbType) {
        int value = argbType.get();
        return new ByteArrayRGBPixelType(ARGBType.red(value), ARGBType.green(value), ARGBType.blue(value));
    }

    private void setValue(int r, int g, int b) {
        valueAccess.setValue(0, (byte)(r & 0xFF));
        valueAccess.setValue(1, (byte)(g & 0xFF));
        valueAccess.setValue(2, (byte)(b & 0xFF));
    }

    private int getValue(int ch) {
        return valueAccess.getValue(ch) & 0xFF;
    }

    @Override
    public int getInteger() {
        int r = getRed();
        int g = getGreen();
        int b = getBlue();

        return ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);
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
        int r = ARGBType.red(f);
        int g = ARGBType.green(f);
        int b = ARGBType.blue(f);
        setValue(r, g, b);
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
        return 0xffffff;
    }

    @Override
    public double getMinValue() {
        return 0;
    }

    @Override
    public int getBitsPerPixel() {
        return 24;
    }
}
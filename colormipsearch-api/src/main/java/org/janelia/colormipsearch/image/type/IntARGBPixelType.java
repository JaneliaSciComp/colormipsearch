package org.janelia.colormipsearch.image.type;

import java.math.BigInteger;

import net.imglib2.img.NativeImg;
import net.imglib2.img.basictypeaccess.IntAccess;
import net.imglib2.type.NativeTypeFactory;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.GenericIntType;

public class IntARGBPixelType extends GenericIntType<IntARGBPixelType> implements RGBPixelType<IntARGBPixelType> {

    private static final NativeTypeFactory< IntARGBPixelType, IntAccess > TYPE_FACTORY = NativeTypeFactory.INT(IntARGBPixelType::new);

    public IntARGBPixelType() {
        this(0xFF000000);
    }

    public IntARGBPixelType(int value) {
        super(value);
    }

    public IntARGBPixelType(NativeImg<?, ? extends IntAccess> imgStorage) {
        super(imgStorage);
    }

    @Override
    public NativeTypeFactory<IntARGBPixelType, IntAccess> getNativeTypeFactory()
    {
        return TYPE_FACTORY;
    }

    @Override
    public IntARGBPixelType duplicateTypeOnSameNativeImg() {
        return new IntARGBPixelType(img);
    }

    @Override
    public int getInteger() {
        return getInt();
    }

    @Override
    public long getIntegerLong() {
        return getInt();
    }

    @Override
    public BigInteger getBigInteger() {
        return BigInteger.valueOf(getInt());
    }

    @Override
    public void setInteger(int f) {
        setInt(f);
    }

    @Override
    public void setInteger(long f) {
        setInt((int) f);
    }

    @Override
    public void setBigInteger(final BigInteger b) {
        setInt(b.intValue());
    }

    @Override
    public double getMaxValue()
    {
        return Integer.MAX_VALUE;
    }

    @Override
    public double getMinValue()
    {
        return Integer.MIN_VALUE;
    }

    @Override
    public IntARGBPixelType createVariable()
    {
        return new IntARGBPixelType();
    }

    @Override
    public IntARGBPixelType copy() {
        return new IntARGBPixelType(getInt());
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
    public IntARGBPixelType fromARGBType(ARGBType argbType) {
        return new IntARGBPixelType(argbType.get());
    }
}

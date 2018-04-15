/*
 * Copyright (c) 2011-2017, Peter Abeles. All Rights Reserved.
 *
 * This file is part of BoofCV (http://boofcv.org).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.boofcv.android;

import android.media.Image;

import java.nio.ByteBuffer;

import boofcv.struct.image.GrayF32;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageBase;
import boofcv.struct.image.ImageGray;
import boofcv.struct.image.ImageType;
import boofcv.struct.image.Planar;

/**
 * Functions for convertin {@link Image} into BoofCV imgae types.
 *
 * @author Peter Abeles
 */
@SuppressWarnings("unchecked")
public class ConvertYuv420_888
{
	public static byte[] declareWork( Image yuv , byte[] work ) {
		Image.Plane planes[] = yuv.getPlanes();

		int workLength = planes[0].getRowStride() + planes[1].getRowStride() + planes[2].getRowStride();

		if( work == null || work.length < workLength )
			return new byte[workLength];
		return work;
	}

	public static void yuvToBoof( Image yuv , ImageBase output , byte work[] )
	{
		if( output instanceof GrayU8 ) {
			yuvToGray(yuv.getPlanes()[0],yuv.getWidth(),yuv.getHeight(),(GrayU8)output);
		} else if( output instanceof  GrayF32 ) {
			yuvToGray(yuv.getPlanes()[0],yuv.getWidth(),yuv.getHeight(),(GrayF32)output, work);
		} else if( output.getImageType().getFamily() == ImageType.Family.PLANAR ) {
			switch( output.getImageType().getDataType()) {
				case U8:
					yuvToPlanerRgbU8(yuv,(Planar<GrayU8>)output,work);
					break;
				case F32:
					yuvToPlanerRgbF32(yuv,(Planar<GrayF32>)output,work);
					break;
				default:
					throw new RuntimeException("Not yet supported");

			}
		} else {
			throw new RuntimeException("Not yet supported");
		}
	}


	/**
	 * Converts an YUV 420 888 into gray
	 *
	 * @param yuv Input: YUV image data
	 * @param output Output: Optional storage for output image.  Can be null.
	 * @param outputType  Output: Type of output image
	 * @param <T> Output image type
	 * @return Gray scale image
	 */
	public static <T extends ImageGray<T>>
	T yuvToGray(Image yuv , T output , byte work[], Class<T> outputType ) {

		if( outputType == GrayU8.class ) {
			return (T) yuvToGray(yuv.getPlanes()[0],yuv.getWidth(),yuv.getHeight(),(GrayU8)output);
		} else if( outputType == GrayF32.class ) {
			return (T) yuvToGray(yuv.getPlanes()[0],yuv.getWidth(),yuv.getHeight(),(GrayF32)output,work);
		} else {
			throw new IllegalArgumentException("Unsupported BoofCV Image Type "+outputType.getSimpleName());
		}
	}


	/**
	 *
	 * @param input The gray plane from YUV
	 * @param output Output: Optional storage for output image.  Can be null.
	 * @return Gray scale image
	 */
	public static GrayF32 yuvToGray(Image.Plane input , int width , int height, GrayF32 output, byte work[] ) {
		if( output != null ) {
			output.reshape(width,height);
		} else {
			output = new GrayF32(width,height);
		}
		if( work.length < width )
			throw new IllegalArgumentException("work array must be at least width long");

		int rowStride = input.getRowStride();

		ByteBuffer buffer = input.getBuffer();
		int indexDst = 0;
		for (int y = 0, indexRow=0; y < height; y++,indexRow += rowStride) {
			buffer.position(indexRow);
			buffer.get(work,0,width);
			for (int x = 0; x < width; x++) {
				output.data[indexDst++] = work[x]&0xFF;
			}
		}

		return output;
	}

	public static GrayU8 yuvToGray(Image.Plane input , int width , int height, GrayU8 output ) {
		if( output != null ) {
			output.reshape(width,height);
		} else {
			output = new GrayU8(width,height);
		}

		int rowStride = input.getRowStride();

		ByteBuffer buffer = input.getBuffer();
		int indexDst = 0;
		for (int y = 0, indexRow=0; y < height; y++,indexRow += rowStride, indexDst += width) {
			buffer.position(indexRow);
			buffer.get(output.data,indexDst,width);
		}

		return output;
	}

	public static Planar<GrayU8> yuvToPlanerRgbU8( Image yuv , Planar<GrayU8> output , byte work[] )
	{
		int width = yuv.getWidth();
		int height = yuv.getHeight();

		if( output != null ) {
			output.setNumberOfBands(3);
			output.reshape(width,height);
		} else {
			output = new Planar(GrayU8.class,width,height,3);
		}

		Image.Plane planes[] = yuv.getPlanes();

		int workLength = planes[0].getRowStride() + planes[1].getRowStride() + planes[2].getRowStride();
		if( work.length < workLength )
			throw new IllegalArgumentException("Work must be at least "+workLength);

		ByteBuffer bufferY = planes[0].getBuffer();
		ByteBuffer bufferU = planes[2].getBuffer();
		ByteBuffer bufferV = planes[1].getBuffer();

		bufferY.position(0);
		bufferU.position(0);
		bufferV.position(0);

		int strideY = planes[0].getRowStride();
		int strideU = planes[1].getRowStride();
		int strideV = planes[2].getRowStride();

		int offsetU = strideY;
		int offsetV = strideY + offsetU;

		int stridePixelUV = planes[1].getPixelStride();

		for (int y = 0, indexOut = 0; y < height; y++) {
			// Read all the data for this row from each plane
			bufferY.get(work,0,strideY);
			if( y%stridePixelUV == 0) {
				bufferU.get(work, offsetU, Math.min(bufferU.remaining(),strideU));
				bufferV.get(work, offsetV, Math.min(bufferV.remaining(),strideV));
			}

			int indexY = 0;
			int indexU = offsetU;
			int indexV = offsetV;

			GrayU8 R = output.getBand(0);
			GrayU8 G = output.getBand(1);
			GrayU8 B = output.getBand(2);

			for (int x = 0; x < width; x++, indexY++, indexOut++ )
			{
				int Y = 1191*((work[indexY] & 0xFF) - 16);
				int CR = (work[ indexU ] & 0xFF) - 128;
				int CB = (work[ indexV] & 0xFF) - 128;

				int stepUV = stridePixelUV*(((x+stridePixelUV-1)%stridePixelUV)^1);
				indexU += stepUV;
				indexV += stepUV;

//				if( y < 0 ) y = 0;
				Y = ((Y >>> 31)^1)*Y;

				int r = (Y + 1836*CR) >> 10;
				int g = (Y - 547*CR - 218*CB) >> 10;
				int b = (Y + 2165*CB) >> 10;

//				if( r < 0 ) r = 0; else if( r > 255 ) r = 255;
//				if( g < 0 ) g = 0; else if( g > 255 ) g = 255;
//				if( b < 0 ) b = 0; else if( b > 255 ) b = 255;

				r *= ((r >>> 31)^1);
				g *= ((g >>> 31)^1);
				b *= ((b >>> 31)^1);

				// The bitwise code below isn't faster than than the if statement below
//				r |= (((255-r) >>> 31)*0xFF);
//				g |= (((255-g) >>> 31)*0xFF);
//				b |= (((255-b) >>> 31)*0xFF);

				if( r > 255 ) r = 255;
				if( g > 255 ) g = 255;
				if( b > 255 ) b = 255;

				R.data[indexOut] = (byte)r;
				G.data[indexOut] = (byte)g;
				B.data[indexOut] = (byte)b;
			}
		}

		return output;
	}

	public static Planar<GrayF32> yuvToPlanerRgbF32( Image yuv , Planar<GrayF32> output , byte work[] )
	{
		int width = yuv.getWidth();
		int height = yuv.getHeight();

		if( output != null ) {
			output.setNumberOfBands(3);
			output.reshape(width,height);
		} else {
			output = new Planar(GrayF32.class,width,height,3);
		}

		Image.Plane planes[] = yuv.getPlanes();

		int workLength = planes[0].getRowStride() + planes[1].getRowStride() + planes[2].getRowStride();
		if( work.length < workLength )
			throw new IllegalArgumentException("Work must be at least "+workLength);

		ByteBuffer bufferY = planes[0].getBuffer();
		ByteBuffer bufferU = planes[2].getBuffer();
		ByteBuffer bufferV = planes[1].getBuffer();

		bufferY.position(0);
		bufferU.position(0);
		bufferV.position(0);

		int strideY = planes[0].getRowStride();
		int strideU = planes[1].getRowStride();
		int strideV = planes[2].getRowStride();

		int offsetU = strideY;
		int offsetV = strideY + offsetU;

		int stridePixelUV = planes[1].getPixelStride();

		for (int y = 0, indexOut = 0; y < height; y++) {
			// Read all the data for this row from each plane
			bufferY.get(work,0,strideY);
			if( y%stridePixelUV == 0) {
				bufferU.get(work, offsetU, Math.min(bufferU.remaining(),strideU));
				bufferV.get(work, offsetV, Math.min(bufferV.remaining(),strideV));
			}

			int indexY = 0;
			int indexU = offsetU;
			int indexV = offsetV;

			GrayF32 R = output.getBand(0);
			GrayF32 G = output.getBand(1);
			GrayF32 B = output.getBand(2);

			for (int x = 0; x < width; x++, indexY++, indexOut++ )
			{
				int Y = 1191*((work[indexY] & 0xFF) - 16);
				int CR = (work[ indexU ] & 0xFF) - 128;
				int CB = (work[ indexV] & 0xFF) - 128;

				int stepUV = stridePixelUV*(((x+stridePixelUV-1)%stridePixelUV)^1);
				indexU += stepUV;
				indexV += stepUV;

//				if( y < 0 ) y = 0;
				Y = ((Y >>> 31)^1)*Y;

				int r = (Y + 1836*CR) >> 10;
				int g = (Y - 547*CR - 218*CB) >> 10;
				int b = (Y + 2165*CB) >> 10;

//				if( r < 0 ) r = 0; else if( r > 255 ) r = 255;
//				if( g < 0 ) g = 0; else if( g > 255 ) g = 255;
//				if( b < 0 ) b = 0; else if( b > 255 ) b = 255;

				r *= ((r >>> 31)^1);
				g *= ((g >>> 31)^1);
				b *= ((b >>> 31)^1);

				// The bitwise code below isn't faster than than the if statement below
//				r |= (((255-r) >>> 31)*0xFF);
//				g |= (((255-g) >>> 31)*0xFF);
//				b |= (((255-b) >>> 31)*0xFF);

				if( r > 255 ) r = 255;
				if( g > 255 ) g = 255;
				if( b > 255 ) b = 255;

				R.data[indexOut] = r;
				G.data[indexOut] = g;
				B.data[indexOut] = b;
			}
		}

		return output;
	}
}

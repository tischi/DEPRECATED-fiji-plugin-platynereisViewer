package de.embl.cba.platynereis.labels.loaders;

import static bdv.img.hdf5.Util.reorder;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5D.H5Dclose;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5D.H5Dget_space;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5D.H5Dopen;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5D.H5Dread;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5S.H5Sclose;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5S.H5Screate_simple;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5S.H5Sget_simple_extent_dims;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5S.H5Sselect_hyperslab;
import static ch.systemsx.cisd.hdf5.hdf5lib.HDF5Constants.*;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

import bdv.img.hdf5.DimsAndExistence;
import bdv.img.hdf5.Util;
import bdv.img.hdf5.ViewLevelId;
import ch.systemsx.cisd.hdf5.IHDF5Reader;

public class HDF5UnsignedLongAccessHack implements IHDF5UnsignedLongAccess
{

	private final IHDF5Reader hdf5Reader;

	private final int fileId;

	private final int numericConversionXferPropertyListID;

	private final long[] reorderedDimensions = new long[ 3 ];

	private final long[] reorderedMin = new long[ 3 ];

	private static final int MAX_OPEN_DATASETS = 48;

	private class OpenDataSet
	{
		final int dataSetId;

		final int fileSpaceId;

		public OpenDataSet( final String cellsPath )
		{
			dataSetId = H5Dopen( fileId, cellsPath, H5P_DEFAULT );
			fileSpaceId = H5Dget_space( dataSetId );
		}

		public void close()
		{
			H5Sclose( fileSpaceId );
			H5Dclose( dataSetId );
		}
	}

	private class OpenDataSetCache extends LinkedHashMap< ViewLevelId, OpenDataSet >
	{
		private static final long serialVersionUID = 1L;

		public OpenDataSetCache()
		{
			super( MAX_OPEN_DATASETS, 0.75f, true );
		}

		@Override
		protected boolean removeEldestEntry( final Entry< ViewLevelId, OpenDataSet > eldest )
		{
			if ( size() > MAX_OPEN_DATASETS )
			{
				eldest.getValue().close();
				return true;
			}
			else
				return false;
		}

		public OpenDataSet getDataSet( final ViewLevelId id )
		{
			OpenDataSet openDataSet = super.get( id );
			if ( openDataSet == null )
			{
				openDataSet = new OpenDataSet( Util.getCellsPath( id ) );
				put( id, openDataSet );
			}
			return openDataSet;
		}
	}

	private final OpenDataSetCache openDataSetCache;

	public HDF5UnsignedLongAccessHack( final IHDF5Reader hdf5Reader ) throws ClassNotFoundException, SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException
	{
		this.hdf5Reader = hdf5Reader;

		final Class< ? > k = Class.forName( "ch.systemsx.cisd.hdf5.HDF5Reader" );
		final Field f = k.getDeclaredField( "baseReader" );
		f.setAccessible( true );
		final Object baseReader = f.get( hdf5Reader );

		final Class< ? > k2 = Class.forName( "ch.systemsx.cisd.hdf5.HDF5BaseReader" );
		final Field f2 = k2.getDeclaredField( "fileId" );
		f2.setAccessible( true );
		fileId = ( ( Integer ) f2.get( baseReader ) ).intValue();

		final Field f3 = k2.getDeclaredField( "h5" );
		f3.setAccessible( true );
		final Object h5 = f3.get( baseReader );

		final Class< ? > k4 = Class.forName( "ch.systemsx.cisd.hdf5.HDF5" );
		final Field f4 = k4.getDeclaredField( "numericConversionXferPropertyListID" );
		f4.setAccessible( true );
		numericConversionXferPropertyListID = ( ( Integer ) f4.get( h5 ) ).intValue();

		openDataSetCache = new OpenDataSetCache();
	}

	@Override
	public synchronized DimsAndExistence getDimsAndExistence( final ViewLevelId id )
	{
		final long[] realDimensions = new long[ 3 ];
		boolean exists = false;
		try
		{
			final OpenDataSet dataset = openDataSetCache.getDataSet( id );
			final long[] dimensions = new long[ H5S_MAX_RANK ];
			final long[] maxDimensions = new long[ H5S_MAX_RANK ];
			final int rank = H5Sget_simple_extent_dims( dataset.fileSpaceId, dimensions, maxDimensions );
			System.arraycopy( dimensions, 0, realDimensions, 0, rank );
			exists = true;
		}
		catch ( final Exception e )
		{}
		if ( exists )
			return new DimsAndExistence( reorder( realDimensions ), true );
		else
			return new DimsAndExistence( new long[] { 1, 1, 1 }, false );
	}

	@Override
	public synchronized long[] readLongMDArrayBlockWithOffset( final int timepoint, final int setup, final int level, final int[] dimensions, final long[] min ) throws InterruptedException
	{
		final long[] dataBlock = new long[ dimensions[ 0 ] * dimensions[ 1 ] * dimensions[ 2 ] ];
		readLongMDArrayBlockWithOffset( timepoint, setup, level, dimensions, min, dataBlock );
		return dataBlock;
	}

	@Override
	public synchronized long[] readLongMDArrayBlockWithOffset( final int timepoint, final int setup, final int level, final int[] dimensions, final long[] min, final long[] dataBlock ) throws InterruptedException
	{
		if ( Thread.interrupted() )
			throw new InterruptedException();
		reorder( dimensions, reorderedDimensions );
		reorder( min, reorderedMin );

		final OpenDataSet dataset = openDataSetCache.getDataSet( new ViewLevelId( timepoint, setup, level ) );
		final int memorySpaceId = H5Screate_simple( reorderedDimensions.length, reorderedDimensions, null );
		H5Sselect_hyperslab( dataset.fileSpaceId, H5S_SELECT_SET, reorderedMin, null, reorderedDimensions, null );
		H5Dread( dataset.dataSetId, H5T_NATIVE_ULONG, memorySpaceId, dataset.fileSpaceId, numericConversionXferPropertyListID, dataBlock );
		H5Sclose( memorySpaceId );

		return dataBlock;
	}

	@Override
	public float[] readLongMDArrayBlockWithOffsetAsFloat( final int timepoint, final int setup, final int level, final int[] dimensions, final long[] min ) throws InterruptedException
	{
		final float[] dataBlock = new float[ dimensions[ 0 ] * dimensions[ 1 ] * dimensions[ 2 ] ];
		readLongMDArrayBlockWithOffsetAsFloat( timepoint, setup, level, dimensions, min, dataBlock );
		return dataBlock;
	}

	@Override
	public float[] readLongMDArrayBlockWithOffsetAsFloat( final int timepoint, final int setup, final int level, final int[] dimensions, final long[] min, final float[] dataBlock ) throws InterruptedException
	{
		if ( Thread.interrupted() )
			throw new InterruptedException();
		reorder( dimensions, reorderedDimensions );
		reorder( min, reorderedMin );

		final OpenDataSet dataset = openDataSetCache.getDataSet( new ViewLevelId( timepoint, setup, level ) );
		final int memorySpaceId = H5Screate_simple( reorderedDimensions.length, reorderedDimensions, null );
		H5Sselect_hyperslab( dataset.fileSpaceId, H5S_SELECT_SET, reorderedMin, null, reorderedDimensions, null );
		H5Dread( dataset.dataSetId, H5T_NATIVE_FLOAT, memorySpaceId, dataset.fileSpaceId, numericConversionXferPropertyListID, dataBlock );
		H5Sclose( memorySpaceId );

		HDF5UnsignedLongAccessHack.unsignedLong( dataBlock );

		return dataBlock;
	}

	@Override
	public void closeAllDataSets()
	{
		for ( final OpenDataSet dataset : openDataSetCache.values() )
			dataset.close();
		openDataSetCache.clear();
	}

	@Override
	public void close()
	{
		closeAllDataSets();
		hdf5Reader.close();
	}

	protected static final void unsignedLong( final float[] pixels )
	{
		// TODO: how to remove the sign?
		for ( int j = 0; j < pixels.length; ++j )
			pixels[ j ] = ((long)pixels[ j ]); // & 0xffffffffffffffff;
	}



}

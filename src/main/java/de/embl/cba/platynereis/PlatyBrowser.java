package de.embl.cba.platynereis;

import bdv.VolatileSpimSource;
import bdv.img.imaris.Imaris;
import bdv.spimdata.SpimDataMinimal;
import bdv.tools.brightness.ConverterSetup;
import bdv.util.Bdv;
import bdv.viewer.Interpolation;
import de.embl.cba.bdv.utils.BdvUtils;
import de.embl.cba.bdv.utils.selection.BdvSelectionEventHandler;
import de.embl.cba.bdv.utils.sources.SelectableARGBConvertedRealSource;
import de.embl.cba.platynereis.ui.BdvSourcesPanel;
import de.embl.cba.platynereis.ui.MainUI;
import de.embl.cba.platynereis.utils.FileUtils;
import de.embl.cba.platynereis.utils.Utils;
import de.embl.cba.tables.TableBdvConnector;
import de.embl.cba.tables.TableUtils;
import de.embl.cba.tables.objects.ObjectCoordinate;
import de.embl.cba.tables.objects.ObjectTablePanel;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewTransformAffine;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.Scale;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;

import static de.embl.cba.bdv.utils.BdvUserInterfaceUtils.showBrightnessDialog;

public class PlatyBrowser
{
    public static final String LABEL_ATTRIBUTES_FOLDER = "label_attributes";
    Bdv bdv;
    public Map< String, PlatySource > dataSources;
    String defaultSource;
    AffineTransform3D emRawDataTransform;
    BdvSourcesPanel legend;
    private final MainUI mainUI;

    public PlatyBrowser( String directory )
    {
        dataSources = Collections.synchronizedMap( new LinkedHashMap() );

        ArrayList< File > imageFiles = getImageFiles( directory );

        ArrayList< File > attributeFiles = getAttributeFiles( directory );

        bdv = initDefaultSourceAndBdv( imageFiles );

        initDataSources( imageFiles, attributeFiles, bdv );

        new Thread(new Runnable(){
            public void run(){
                configureObjectSources( );
            }
        }).start();

        new Thread(new Runnable(){
            public void run(){
                fetchProsprSources( );
            }
        }).start();

        mainUI = new MainUI( this.bdv, this );

        legend = mainUI.getBdvSourcesPanel();
    }

    public ArrayList< PlatySource > getPlatySources( ArrayList< String > selectedSourceNames )
	{
		final ArrayList< PlatySource > selectedPlatySources = new ArrayList<>();

		for ( String sourceName : dataSources.keySet() )
		{
			if ( selectedSourceNames.contains( sourceName ) )
			{
				selectedPlatySources.add( dataSources.get( sourceName ));
			}
		}
		return selectedPlatySources;
	}

    public ArrayList< File > getAttributeFiles( String directory )
    {
        return new ArrayList< File >( Arrays.asList( new File( directory + File.separator + LABEL_ATTRIBUTES_FOLDER ).listFiles() ) );
    }


    public static ArrayList< File > getFiles( String inputDirectory, String filePattern )
    {
        Utils.log( "Fetching files..." );
        final ArrayList< File > fileList = FileUtils.getFileList( new File( inputDirectory ), filePattern );
        Utils.log( "Number of files: " +  fileList.size() );
        return fileList;
    }

    public ArrayList< File > getImageFiles( String directory )
    {
        ArrayList< File > imageFiles = new ArrayList< File >( Arrays.asList( new File( directory ).listFiles() ) );
        Collections.sort( imageFiles, new SortFilesIgnoreCase());
        return imageFiles;
    }

    public MainUI getMainUI()
    {
        return mainUI;
    }

    public Bdv getBdv()
    {
        return bdv;
    }

    public class SortFilesIgnoreCase implements Comparator<File>
    {
        public int compare( File o1, File o2 ) {
            String s1 = o1.getName();
            String s2 = o2.getName();
            return s1.toLowerCase().compareTo(s2.toLowerCase());
        }
    }

    public String getDefaultSourceName()
    {
        return defaultSource;
    }

    private void configureObjectSources()
    {
        Set< String > names = dataSources.keySet();

        for (  String name : names )
        {
            PlatySource source = dataSources.get( name );

            if ( source.isLabelSource && source.attributeFile != null )
            {
                try
                {
                    final JTable jTable = TableUtils.loadTable( source.attributeFile, "\t" );
                    final ObjectTablePanel objectTablePanel = new ObjectTablePanel( jTable );
                    objectTablePanel.setCoordinateColumn( ObjectCoordinate.Label, "label_id" );
                    objectTablePanel.setCoordinateColumn( ObjectCoordinate.X, "com_x_microns" );
                    objectTablePanel.setCoordinateColumn( ObjectCoordinate.Y, "com_y_microns" );
                    objectTablePanel.setCoordinateColumn( ObjectCoordinate.Z, "com_z_microns" );
                    objectTablePanel.showPanel();

                    final TableBdvConnector tableBdvConnector = new TableBdvConnector( objectTablePanel, source.bdvSelectionEventHandler );
                    tableBdvConnector.setSelectionByAttribute( true );
                }
                catch ( IOException e )
                {
                    e.printStackTrace();
                }
            }
        }
    }

    public void run()
    {

    }

    public void removeDataSource( String dataSourceName )
    {
        // TODO: extra class for dataSources ?
        if ( dataSources.get( dataSourceName ).bdvStackSource != null && dataSources.get( dataSourceName ).bdvStackSource.getBdvHandle() != null )
        {
            dataSources.get( dataSourceName ).bdvStackSource.removeFromBdv();
        }
    }

    public void setDataSourceColor( String sourceName, Color color )
    {
        // TODO: extra class for dataSources ?
        dataSources.get( sourceName ).bdvStackSource.setColor( Utils.asArgbType( color ) );
        dataSources.get( sourceName ).color = color;
    }


    public void setBrightness( String sourceName )
    {

        final int sourceId = BdvUtils.getSourceIndex( bdv, sourceName );
        final ConverterSetup converterSetup = bdv.getBdvHandle().getSetupAssignments().getConverterSetups().get( sourceId );

		showBrightnessDialog( sourceName, converterSetup );

//		GenericDialog gd = new GenericDialog( "LUT max value" );
//        gd.addNumericField( "LUT max value: ", dataSources.get( sourceName ).maxLutValue, 0 );
//        gd.showDialog();
//        if ( gd.wasCanceled() ) return;
//
//        int max = ( int ) gd.getNextNumber();
//
//        dataSources.get( sourceName ).bdvStackSource.setDisplayRange( 0.0, max );
//        dataSources.get( sourceName ).maxLutValue = max;

    }


//    private void setName( String name, PlatySource source )
//    {
//        if ( source.spimData != null )
//        {
//            source.spimData.getSequenceDescription().getViewSetupsOrdered().get( 0 ).getChannel().setName( name );
//        }
//    }

    private SpimDataMinimal openImaris( File file, double[] calibration )
    {
        SpimDataMinimal spimDataMinimal;

        try
        {
            spimDataMinimal = Imaris.openIms( file.toString() );
        }
        catch ( IOException e )
        {
            e.printStackTrace();
            return null;
        }

        setScale( spimDataMinimal, calibration );

        return spimDataMinimal;

    }

    private void setScale( SpimDataMinimal spimDataMinimal, double[] calibration )
    {
        final AffineTransform3D affineTransform3D = new AffineTransform3D();
        final Scale scale = new Scale( calibration );
        affineTransform3D.preConcatenate( scale );
        final ViewTransformAffine calibrationTransform = new ViewTransformAffine( "calibration", affineTransform3D );
        spimDataMinimal.getViewRegistrations().getViewRegistration( 0,0 ).identity();
        spimDataMinimal.getViewRegistrations().getViewRegistration( 0,0 ).preconcatenateTransform( calibrationTransform );

        final FinalVoxelDimensions voxelDimensions = new FinalVoxelDimensions( "micrometer", calibration );
        BasicViewSetup basicViewSetup = new BasicViewSetup(  0, "view", null, voxelDimensions );
        spimDataMinimal.getSequenceDescription().getViewSetupsOrdered().set( 0, basicViewSetup);
    }

    private Bdv initDefaultSourceAndBdv( ArrayList< File > imageFiles )
    {
        for ( File file : imageFiles )
        {
            final String fileName = file.getName();

            if (  fileName.contains( Constants.DEFAULT_EM_RAW_FILE_ID ) && fileName.endsWith( Constants.BDV_XML_SUFFIX ) )
            {
                PlatySource source = new PlatySource();

                source.name = Constants.DEFAULT_EM_RAW_FILE_ID;
                source.file = file;

                dataSources.put( source.name, source );
                defaultSource = source.name;

                source.color = Constants.DEFAULT_EM_RAW_COLOR;
                source.maxLutValue = 255;
                source.spimData = Utils.openSpimData( file );

                bdv = Utils.showSourceInBdv( dataSources.get( defaultSource ), bdv );
                bdv.getBdvHandle().getViewerPanel().setInterpolation( Interpolation.NLINEAR );


                return bdv;
            }

        }

        return null;

    }

    private void initDataSources( ArrayList< File > imageFiles, ArrayList< File > attributeFiles, Bdv bdv )
    {
        for ( File file : imageFiles )
        {
            final String fileName = file.getName();

            if ( fileName.contains( Constants.DEFAULT_EM_RAW_FILE_ID ) )
            {
               continue; // has been already initialised before
            }

            if ( ! fileName.endsWith( Constants.BDV_XML_SUFFIX ) && ! fileName.endsWith( Constants.IMARIS_SUFFIX ) )
            {
                continue;
            }

            PlatySource source = new PlatySource();

            source.name = getSourceName( file );

            dataSources.put( source.name, source );

            source.file = file;

            if ( fileName.contains( Constants.EM_FILE_ID ) )
            {
                source.maxLutValue = 255;
            }
            else
            {
                source.maxLutValue = 1000; // to render the binary prospr more transparent
            }


            if ( fileName.contains( Constants.EM_FILE_ID ) )
            {
                if ( fileName.endsWith( Constants.BDV_XML_SUFFIX ) )
                {
                    source.spimData = Utils.openSpimData( file );
                }
                else if ( fileName.contains( Constants.IMARIS_SUFFIX ) )
                {
                    double[] calibration = new double[] { 0.01, 0.01, 0.025 };
                    source.spimDataMinimal = openImaris( file, calibration );
                    source.isSpimDataMinimal = true;
                }


                if ( fileName.contains( Constants.EM_RAW_FILE_ID )  )
                {
                    source.color = Constants.DEFAULT_EM_RAW_COLOR;
                }

                if ( fileName.contains( Constants.EM_SEGMENTED_FILE_ID ) )
                {
                    source.color = Constants.DEFAULT_EM_SEGMENTATION_COLOR;
                }

                if ( fileName.contains( Constants.LABELS_FILE_ID ) )
                {
                	source.labelSource = new SelectableARGBConvertedRealSource(
							new VolatileSpimSource(
									source.spimData,
									0,
									source.name ) );

					source.bdvSelectionEventHandler = new BdvSelectionEventHandler(
                            bdv,
							source.labelSource );

					source.isLabelSource = true;

					source.spimData = null;

					source.maxLutValue = 600;

                    for ( File attributeFile : attributeFiles )
                    {
                        if ( attributeFile.toString().contains( source.name ) )
                        {
                            // doing like this (i.e. without a break) should take the latest version
                            source.attributeFile = attributeFile;
                        }
                    }
                }
            }
            else // gene
            {
                source.color = Constants.DEFAULT_GENE_COLOR;
            }

        }

    }


    private void fetchProsprSources( )
    {
        Set< String > names = dataSources.keySet();

        for (  String name : names )
        {
            PlatySource source = dataSources.get( name );

            if ( source.file.getName().contains( Constants.EM_FILE_ID ) ) continue;

            if ( source.file.getName().endsWith( Constants.BDV_XML_SUFFIX ) )
            {
                if ( source.spimData == null )
                {
                    source.spimData = Utils.openSpimData( source.file );

//                    if ( labelSource.file.getName().contains( Constants.NEW_PROSPR ) )
//                    {
                    if ( ! source.file.getName().contains( Constants.NEW_PROSPR )
                            && ! source.file.getName().contains( Constants.AVG_PROSPR ) )
                    {
                        ProSPrRegistration.setInverseEmSimilarityTransform( source );
                    }

//                    }
                }
            }
        }
    }


    private static String getSourceName( File file )
    {
        String dataSourceName = null;

        if ( file.getName().endsWith( Constants.BDV_XML_SUFFIX ) )
		{
			dataSourceName = file.getName().replaceAll( Constants.BDV_XML_SUFFIX, "" );
		}
		else if ( file.getName().endsWith( Constants.IMARIS_SUFFIX ) )
		{
			dataSourceName = file.getName().replaceAll( Constants.IMARIS_SUFFIX, "" );
		}
		if ( dataSourceName.contains( Constants.NEW_PROSPR ) )
		{
			dataSourceName= dataSourceName.replace( Constants.NEW_PROSPR, Constants.MEDS );
		}
		else if ( dataSourceName.contains( Constants.AVG_PROSPR ) )
		{
			dataSourceName = dataSourceName.replace( Constants.AVG_PROSPR, Constants.SPMS );
		}
		else if ( ! dataSourceName.contains( Constants.EM_FILE_ID ) )
		{
			dataSourceName = dataSourceName + Constants.OLD;
		}

        return dataSourceName;
    }

}
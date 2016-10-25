package sc.fiji.threedviewer.io;

import java.io.File;

import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.command.Command;

import sc.fiji.ThreeDViewer;

@Plugin(type = Command.class, 
		menuPath = "ThreeDViewer>Import>STL", label = "Import STL")
public class ImportSTL  implements Command {
	
	@Parameter
	private File stlFile;

	@Override
	public void run() {
		if ( stlFile != null )
		{
			try
			{
				ThreeDViewer.addSTL( stlFile.getAbsolutePath() );
			}
			catch ( final Exception e )
			{
				throw new RuntimeException( e );
			}
		}		
	}

}

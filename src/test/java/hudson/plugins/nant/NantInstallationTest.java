package hudson.plugins.nant;

import java.io.File;

import junit.framework.Assert;
import hudson.plugins.nant.NantBuilder.NantInstallation;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Enclosed;

@RunWith(Enclosed.class)
public class NantInstallationTest
{
	/**
	 * Unit tests for NAnt installations running on a Windows platform
	 * 
	 * @author Justin Holzer (jsholzer@gmail.com)
	 */
	public static class WhenPlatformIsWindows
	{
		private NantInstallation nantInstall;
		
		@Before
		public void setUp()
		{
			System.setProperty("os.name", "Windows 7");
			nantInstall = new NantInstallation("nant-test", "C:\\Program Files\\nant\nant-0.85");
		}
		
		@Test
		public void ShouldReturnExecutableForWindows()
		{
			String expected = "nant.exe";
			
			Assert.assertEquals(expected, NantInstallation.getExecutableName().toLowerCase());
		}
		
		@Test
		public void ExecutableFileShouldReferenceWindowsExecutable()
		{
			String expectedPath =
				(new File(nantInstall.getNantHome().toLowerCase() + "/bin/nant.exe"))
					.getAbsolutePath()
					.toLowerCase();
			
			Assert.assertEquals(expectedPath, nantInstall.getExecutableFile().getAbsolutePath().toLowerCase());
		}
	}
	
	/**
	 * Unit tests for NAnt installations running on non-Windows platforms (Linux, Max, etc.)
	 * 
	 * @author Justin Holzer (jsholzer@gmail.com)
	 */
	public static class WhenPlatformIsNotWindows
	{
		private NantInstallation nantInstall;
		
		@Before
		public void setUp()
		{
			System.setProperty("os.name", "Linux");
			nantInstall = new NantInstallation("nant-test", "/usr/local");
		}
		
		@Test
		public void ShouldReturnExecutableForUnix()
		{
			String expected = "nant";
			
			Assert.assertEquals(expected, NantInstallation.getExecutableName());
		}
		
		@Test
		public void ExecutableFileShouldReferenceUnixExecutable()
		{
			String expectedPath = (new File(nantInstall.getNantHome().toLowerCase() + "/bin/nant")).getAbsolutePath();
			
			Assert.assertEquals(expectedPath, nantInstall.getExecutableFile().getAbsolutePath());
		}
	}
}

package com.ugcs.mavlink.generator;

import java.io.IOException;

import javax.xml.bind.JAXBException;

import com.sun.codemodel.JClassAlreadyExistsException;

public class Mavlink2Java {
	public static void main(String[] args) throws IOException, JAXBException, JClassAlreadyExistsException {
		String mavlinkVersion = "1.0";
		String sourcePath = "src/main/java";
		String packageName = null;
		boolean expandedSystemId = false;
		String messageDefinitionsPath = null;
		
		for (int i = 0; i < args.length; ++i) {
			if (args[i].equals("-v")) {
				if (i + 1 == args.length)
					usage();
				mavlinkVersion = args[++i];
				continue;
			}
			if (args[i].equals("-d")) {
				if (i + 1 == args.length)
					usage();
				sourcePath = args[++i];
				continue;
			}
			if (args[i].equals("-p")) {
				if (i + 1 == args.length)
					usage();
				packageName = args[++i];
				continue;
			}
			if (args[i].equals("-e")) {
				expandedSystemId = true;
				continue;
			}
			messageDefinitionsPath = args[i];
			break;
		}
		if (messageDefinitionsPath == null)
			usage();
		
		Generator generator = new Generator(mavlinkVersion, expandedSystemId);
		generator.generate(messageDefinitionsPath, sourcePath, packageName);
	}
	
	public static void usage() {
		System.err.println("mav2java [-p pkg] [-d src] [-v version] [-e] path");
		System.err.println();
		System.err.println("-p    package name of the generated Java classes; source files will be");
		System.err.println("      placed to the extra directories relative to the -d source path");
		System.err.println("-d    base directory path of the generated source files");
		System.err.println("-v    MAVLink protocol version (0.9 or 1.0)");
		System.err.println("-e    extended system id flag (UgCS specific option);");
		System.err.println("path  root MAVLink XML definitions file");
		System.exit(1);
	}
}

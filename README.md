# platform
Proofpoint Platform

You may find more info about the Company at http://www.proofpoint.com/


The Proofpoint Platform may be built, installed and tested using at least 2 different approaches:

1.  Interpreting through MAVEN the project descriptions, located in pom.xml files.

	In order to use this approach you need to have JAVA and MAVEN installed on your system.
	

2.  Automatically starting the builds through Travis-Ci system, interpreting travis.yml files.

	In order to use this approach you need to have account at https://travis-ci.org/
	Register for an account at https://travis-ci.org/ in case you don't have one.
	After that follow documentation at http://docs.travis-ci.com/ 
	

The following procedure describes how to use the first approach.

-	Start you terminal (or gitbash for Windows platform) and clone current repository

		git clone ...


-	Start building the platform by installing the library package:

		# switch to the library folder and create the library package
		cd library
		mvn package
		
		# in case of SUCCESS - install it in your local repo
		mvn clean install
		
		
-	Verify that previous step completed with SUCCESS message.
	Now you can try installing and testing components of the Proofpoint Platform.
	Do this from the folder, where parent pom.xml file is located
	
		cd ..
		mvn install
		
	Observe how many modules had been installed successfully, test all of them and create test reports
	
		mvn test site
		
	Make sure that testing of all installed Proofpoint components finished with SUCCESS
	In the other case follow instructions on your terminal window or do the following steps
		

-	Make a list of Proofpoint Platform components, in which initial build failed.
	With high probability this list will include:
		concurrent
		reporting
		stats
		http-client
		discovery
		reporting-client
		event
		jaxrs
		jmx
		jmx-http
		rest-server-base
		sample-server
		
		
		
	For every component in above list (order is important) execute the following commands:
		
		# switch to the folder containing specified <component>
		# where <component> will be concurrent, reporting, stats, ...
		cd <component>
		
		# build <component> package, install, test it and create reports
		mvn install test site
		
	Verify that installation and testing of every Proofpoint components finished with SUCCESS

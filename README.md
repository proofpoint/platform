# platform
Proofpoint Platform

You may find more info about the Company is at http://www.proofpoint.com/

In order to build the Proofpoint Platform you need to have MAVEN installed on your system

1. Start building the platform by installing the library package:

		# switch to the library folder and create the library package
		cd library
		mvn package
		
		# in case of SUCCESS install it in your local repo
		mvn clean install
		
		
2.  Verify that previous step completed with SUCCESS message.
	Now you can try installing and testing components of the Proofpoint Platform.
	Do this from the folder, where parent pom.xml file is located
	
		cd ..
		mvn install
		
	Observe how many modules had been installed successfully and test them
	
		mvn test
		
	Make sure that testing of all installed Proofpoint components finished with SUCCESS
	In the other case follow instructions on the screem
		

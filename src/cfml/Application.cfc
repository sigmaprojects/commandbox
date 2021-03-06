/**
*********************************************************************************
* Copyright Since 2005 ColdBox Platform by Ortus Solutions, Corp
* www.coldbox.org | www.ortussolutions.com
********************************************************************************
* @author Brad Wood, Luis Majano, Denny Valliant
* Main Application Bootstrap
*/
component{

	this.name = "CommandBox CLI";
	this.sessionmanagement = "false";

	// Move everything over to this mapping which is the "root" of our app
	CFMLRoot = getDirectoryFromPath( getMetadata( this ).path );
	this.mappings[ '/commandbox' ] 		= CFMLRoot;
	this.mappings[ '/commandbox-home' ] = expandPath( CFMLRoot & '/../' );
	this.mappings[ '/wirebox' ] 		= CFMLRoot & '/system/wirebox';

	function onApplicationStart(){
		new wirebox.system.ioc.Injector( 'commandbox.system.config.WireBox' );
		return true;
	}

	function onApplicationStop(){
		application.wirebox.shutdown();
	}
	
	function onError( any exception, string eventName ) {
		
		// Try to log this to LogBox
		try {
    		application.wireBox.getLogBox().getRootLogger().error( '#exception.message# #exception.detail ?: ''#', exception.stackTrace );
    	// If it fails no worries, LogBox just probably isn't loaded yet.
		} catch ( Any e ) {}
		
		// Give nicer message to user
		var err = arguments.exception;
    	var CR = chr( 13 );
    	systemOutput( 'BOOM GOES THE DYNAMITE!!', true );
    	systemOutput( 'We''re truly sorry, but something horrible has gone wrong when starting up CommandBox.', true );
    	systemOutput( 'Here''s what we know:.', true );
    	systemOutput( '', true );
    	systemOutput( 'Message:', true );
    	systemOutput( '#err.message#', true );
    	systemOutput( '', true );
		if( structKeyExists( err, 'detail' ) ) {
    		systemOutput( '#err.detail#', true );
		}
		if( structKeyExists( err, 'tagcontext' ) ){
			var lines = arrayLen( err.tagcontext );
			if( lines != 0 ){
				systemOutput( 'Tag Context:', true );
				for( var idx=1; idx <= lines; idx++) {
					var tc = err.tagcontext[ idx ];
					if( len( tc.codeprinthtml ) ){
						if( idx > 1 ) {
    						systemOutput( 'called from ' );
						}
   						systemOutput( '#tc.template#: line #tc.line#', true );
					}
				}
			}
		}
    	systemOutput( '', true );
    	systemOutput( '#err.stacktrace#', true );

    	//writeDump(var=arguments.exception, output="console"); 	
    	
		// Give them a chance to read it
		sleep( 30000 );
	}

}
$(document).ready(function() {


	$( ".theme-default" ).click(function() {
	  $( "body" ).removeClass( "theme" );
	  $( "body" ).removeClass( "red-theme" );
	});
    
	$( ".theme-red" ).click(function() {
	  $( "body" ).addClass( "red-theme" );
	  $( "body" ).removeClass( "theme" );
	});
    
	$( ".theme-blue" ).click(function() {
	  $( "body" ).addClass( "theme" );
	  $( "body" ).removeClass( "red-theme" );
	});


});

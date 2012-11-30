var TestPower = {
    conn: null,
    fileHandle: null,
    fileName: null,

    main: function() {
        // Connect to the simualtion process
        Saguaro.getProcess(null, this.onConnect.bind(this));
    },

    onConnect: function(result) {
	if (result.code != 0) { this.exit("connect failed: "+result.toString(), 1); }

        this.fileName = "current.dat";

        // We now have a connection to the simulation
	this.conn = result.getData();
	println("onConnect: " + this.conn.toString());

        // Register for events from the simulation
        this.listener = Sonoran.Registry.addListener(this.onEvent.bind(this), Sonoran.Event.UPDATE);
        // open file for the current trace append
        this.fileHandle = OSFile.fopen(this.fileName, "w+");

    },

    onEvent: function(event) {
        // We received an update event

        var mote = event.mote; // the mote which generated the event

        // check the event type
        var device = event.device;
        if (device != "current") return; // ignore events other than current traces

        // analyze the current trace data
        var trace = event.trace;
        for (var i=0; i<trace.length; i++){
	    // print current trace in human readable format
            printf("mote=%s \t time=%15d ns \t curr=%15d nA \t reason=%5s \n",
		   mote, trace[i].time, trace[i].current, trace[i].reason);

            // write the data to a file ready for plotting ...
            OSFile.fwrite(this.fileHandle, sprintf("%d %d\n", trace[i].time, trace[i].current));
        }

        printf("Appending data to %s ... \n", this.fileName);
	OSFile.fflush(this.fileHandle);
    }

};


// -----------------------------------------------------------------------------
TestPower.main();

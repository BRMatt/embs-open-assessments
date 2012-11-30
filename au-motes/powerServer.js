
PowerGraph = {};

PowerGraph.Server = {

	/**
	 * The channel the power graph server broadcasts on
	 */
	CHANNEL_NAME: "PowerGraphChannel",
	
	init: function() {
		Channels.Registry.addFactory(new Channels.Factory(this.CHANNEL_NAME, function(client, name) {
			return new Channels.Channel(name, client);
		}));
		
		this.generate();
	}
	
	onTimeout: function() {
		this.timer = null;
		this.generate();
	},
	
	cancelTimer: function() {
		if(this.timer) {
			this.timer.cancel();
			this.timer = null;
		}
	},
	
	generate: function() {
		Channels.registry.broadcastObject(42, this.CHANNEL_NAME);
		this.timer = Timer.timeoutIn(1000, this.onTimeout.bind(this));
	}
	
	
};
function TestTypesMsg(msg) {

	this.boolParam = UNDEFINE;

	this.doubleParam = UNDEFINE;

	this.intPara = UNDEFINE;

	this.longParam = 0;

	this.strParam = "";

	if (msg) {
		this.boolParam = msg[4];
		this.doubleParam = msg[3];
		this.intPara = msg[2];
		this.longParam = msg[1];
		this.strParam = msg[0];	
	}

	this.getMessage = function() {
		return "[" + "\"" + this.strParam + "\"" + "," + this.longParam + "," + this.intPara + "," + this.doubleParam + "," + this.boolParam + "]";
	}	
}
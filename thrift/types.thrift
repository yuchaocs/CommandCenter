namespace java edu.umich.clarity.thrift

struct THostPort {
	1: string ip;
	2: i32 port;
}

struct QuerySpec {
	1: optional string name;
	2: binary input;
	3: list<i64> timestamp;
	4: double budget; 
}

struct RegMessage {
	1: string app_name;
	2: THostPort endpoint;
	3: double budget;
}

struct RegReply {
	1: list<THostPort> service_list;
	2: bool final_stage = false;
}
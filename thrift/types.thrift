namespace java edu.umich.clarity.thrift

struct THostPort {
	1: string ip;
	2: i32 port;
}

struct LatencySpec {
	// servicetype_ip_port
	1: string instance_id;
	2: i64 queuing_start_time;
	// serving start time equals queuing end time
	3: i64 serving_start_time;
	4: i64 serving_end_time;
}

struct QuerySpec {
	1: optional string name;
	2: binary input;
	3: list<LatencySpec> timestamp;
	4: double budget;
	5: double floatingBudget;
}

struct RegMessage {
	1: string app_name;
	2: THostPort endpoint;
	3: double budget;
}

//struct RegReply {
//	1: list<THostPort> service_list;
//	2: bool final_stage = false;
//}
include "types.thrift"

namespace java edu.umich.clarity.thrift

service SchedulerService {
	void registerBackend(1: types.RegMessage message),
	void enqueueFinishedQuery(1: types.QuerySpec query),
	types.THostPort consultAddress(1: string serviceType),
	i32 warmupCount()
}

service IPAService {
	i32 reportQueueLength(),
	void updatBudget(1: double budget),
	void submitQuery(1: types.QuerySpec query),
	i32 stealParentInstance(1: types.THostPort hostPort),
	list<types.QuerySpec> stealQueuedQueries()
}

service NodeManagerService {
	types.THostPort launchServiceInstance(1: string serviceType, 2: double budget)
}
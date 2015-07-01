include "types.thrift"

namespace java edu.umich.clarity.thrift

service SchedulerService {
	void registerBackend(1: types.RegMessage message),
	void enqueueFinishedQuery(1: types.QuerySpec query)
	types.THostPort consultAddress(1: string serviceType)
}

service IPAService {
	void updatBudget(1: double budget),
	void submitQuery(1: types.QuerySpec query)
}

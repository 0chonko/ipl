package ibis.satin.impl;

import ibis.util.Timer;

public abstract class Stats extends TupleSpace {

	void initTimers() {
		if (totalTimer == null)
			totalTimer = new Timer();
		if (stealTimer == null)
			stealTimer = new Timer();
		if (handleStealTimer == null)
			handleStealTimer = new Timer();
		if (abortTimer == null)
			abortTimer = new Timer();
		if (idleTimer == null)
			idleTimer = new Timer();
		if (pollTimer == null)
			pollTimer = new Timer();
		if (tupleTimer == null)
			tupleTimer = new Timer();
		if (invocationRecordWriteTimer == null)
			invocationRecordWriteTimer = new Timer();
		if (invocationRecordReadTimer == null)
			invocationRecordReadTimer = new Timer();
		if (tupleOrderingWaitTimer == null)
			tupleOrderingWaitTimer = new Timer();
		if (tupleOrderingSeqTimer == null)
			tupleOrderingSeqTimer = new Timer();
		if (lookupTimer == null)
			lookupTimer = new Timer();
		if (updateTimer == null)
			updateTimer = new Timer();
		if (handleUpdateTimer == null)
			handleUpdateTimer = new Timer();
		if (handleLookupTimer == null)
			handleLookupTimer = new Timer();
		if (crashTimer == null)
			crashTimer = new Timer();
		if (redoTimer == null)
			redoTimer = new Timer();
		if (addReplicaTimer == null)
			addReplicaTimer = new Timer();
	}

	protected StatsMessage createStats() {
		StatsMessage s = new StatsMessage();

		s.spawns = spawns;
		s.jobsExecuted = jobsExecuted;
		s.syncs = syncs;
		s.aborts = aborts;
		s.abortMessages = abortMessages;
		s.abortedJobs = abortedJobs;

		s.stealAttempts = stealAttempts;
		s.stealSuccess = stealSuccess;
		s.tupleMsgs = tupleMsgs;
		s.tupleBytes = tupleBytes;
		s.stolenJobs = stolenJobs;
		s.stealRequests = stealRequests;
		s.interClusterMessages = interClusterMessages;
		s.intraClusterMessages = intraClusterMessages;
		s.interClusterBytes = interClusterBytes;
		s.intraClusterBytes = intraClusterBytes;

		s.stealTime = stealTimer.totalTimeVal();
		s.handleStealTime = handleStealTimer.totalTimeVal();
		s.abortTime = abortTimer.totalTimeVal();
		s.idleTime = idleTimer.totalTimeVal();
		s.idleCount = idleTimer.nrTimes();
		s.pollTime = pollTimer.totalTimeVal();
		s.pollCount = pollTimer.nrTimes();
		s.tupleTime = tupleTimer.totalTimeVal();
		s.tupleWaitTime = tupleOrderingWaitTimer.totalTimeVal();
		s.tupleWaitCount = tupleOrderingWaitTimer.nrTimes();
		s.tupleSeqTime = tupleOrderingSeqTimer.totalTimeVal();
		s.tupleSeqCount = tupleOrderingSeqTimer.nrTimes();

		s.invocationRecordWriteTime = invocationRecordWriteTimer.totalTimeVal();
		s.invocationRecordWriteCount = invocationRecordWriteTimer.nrTimes();
		s.invocationRecordReadTime = invocationRecordReadTimer.totalTimeVal();
		s.invocationRecordReadCount = invocationRecordReadTimer.nrTimes();

		//fault tolerance
		if (FAULT_TOLERANCE) {
			s.tableUpdates = globalResultTable.numUpdates;
			//each remote lookup == 2 lookups in table (local & remote) -- must
			// clean up this code later
			s.tableLookups = globalResultTable.numLookups
					- globalResultTable.numRemoteLookups;
			s.tableSuccessfulLookups = globalResultTable.numLookupsSucceded
					- globalResultTable.numRemoteLookups;
			s.tableRemoteLookups = globalResultTable.numRemoteLookups;
			s.killedOrphans = killedOrphans;

			s.tableLookupTime = lookupTimer.totalTimeVal();
			s.tableUpdateTime = updateTimer.totalTimeVal();
			s.tableHandleUpdateTime = handleUpdateTimer.totalTimeVal();
			s.tableHandleLookupTime = handleLookupTimer.totalTimeVal();
			s.crashHandlingTime = crashTimer.totalTimeVal();
			s.addReplicaTime = addReplicaTimer.totalTimeVal();
		}

		return s;
	}

	protected void printStats() {
		int size;

		synchronized (this) {
			// size = victims.size();
			// No, this is one too few. (Ceriel)
			size = victims.size() + 1;
		}

		// add my own stats
		StatsMessage me = createStats();
		totalStats.add(me);

		java.text.NumberFormat nf = java.text.NumberFormat.getInstance();
		//		pf.setMaximumIntegerDigits(3);
		//		pf.setMinimumIntegerDigits(3);

		// for percentages
		java.text.NumberFormat pf = java.text.NumberFormat.getInstance();
		pf.setMaximumFractionDigits(3);
		pf.setMinimumFractionDigits(3);
		pf.setGroupingUsed(false);

		out
				.println("-------------------------------SATIN STATISTICS--------------------------------");
		if (SPAWN_STATS) {
			out.println("SATIN: SPAWN:       " + nf.format(totalStats.spawns)
					+ " spawns, " + nf.format(totalStats.jobsExecuted)
					+ " executed, " + nf.format(totalStats.syncs) + " syncs");
			if (ABORTS) {
				out.println("SATIN: ABORT:       "
						+ nf.format(totalStats.aborts) + " aborts, "
						+ nf.format(totalStats.abortMessages) + " abort msgs, "
						+ nf.format(totalStats.abortedJobs) + " aborted jobs");
			}
		}

		if (TUPLE_STATS) {
			out.println("SATIN: TUPLE_SPACE: "
					+ nf.format(totalStats.tupleMsgs) + " bcasts, "
					+ nf.format(totalStats.tupleBytes) + " bytes");
		}

		if (POLL_FREQ != 0 && POLL_TIMING) {
			out.println("SATIN: POLL:        poll count = "
					+ nf.format(totalStats.pollCount));
		}

		if (STEAL_STATS) {
			out
					.println("SATIN: STEAL:       "
							+ nf.format(totalStats.stealAttempts)
							+ " attempts, "
							+ nf.format(totalStats.stealSuccess)
							+ " successes ("
							+ pf
									.format(((double) totalStats.stealSuccess / totalStats.stealAttempts) * 100.0)
							+ " %)");

			out.println("SATIN: MESSAGES:    intra "
					+ nf.format(totalStats.intraClusterMessages) + " msgs, "
					+ nf.format(totalStats.intraClusterBytes)
					+ " bytes; inter "
					+ nf.format(totalStats.interClusterMessages) + " msgs, "
					+ nf.format(totalStats.interClusterBytes) + " bytes");
		}

		if (GRT_STATS) {
			out.println("SATIN: GLOBAL_RESULT_TABLE: updates "
					+ nf.format(totalStats.tableUpdates) + ",lookups "
					+ nf.format(totalStats.tableLookups) + ",successful "
					+ nf.format(totalStats.tableSuccessfulLookups) + ",remote "
					+ nf.format(totalStats.tableRemoteLookups));
		}

		if (FT_ABORT_STATS) {
			out.println("SATIN: ORPHAN JOBS: killed orphans "
					+ nf.format(totalStats.killedOrphans));
		}

		out
				.println("-------------------------------SATIN TOTAL TIMES-------------------------------");
		if (STEAL_TIMING) {
			out.println("SATIN: STEAL_TIME:             total "
					+ Timer.format(totalStats.stealTime)
					+ " time/req    "
					+ Timer.format(totalStats.stealTime
							/ totalStats.stealAttempts));
			out.println("SATIN: HANDLE_STEAL_TIME:      total "
					+ Timer.format(totalStats.handleStealTime)
					+ " time/handle "
					+ Timer.format((totalStats.handleStealTime)
							/ totalStats.stealAttempts));

			out.println("SATIN: SERIALIZATION_TIME:     total "
					+ Timer.format(totalStats.invocationRecordWriteTime)
					+ " time/write  "
					+ Timer.format(totalStats.invocationRecordWriteTime
							/ totalStats.stealSuccess));
			out.println("SATIN: DESERIALIZATION_TIME:   total "
					+ Timer.format(totalStats.invocationRecordReadTime)
					+ " time/read   "
					+ Timer.format(totalStats.invocationRecordReadTime
							/ totalStats.stealSuccess));
		}

		if (ABORT_TIMING) {
			out.println("SATIN: ABORT_TIME:             total "
					+ Timer.format(totalStats.abortTime) + " time/abort  "
					+ Timer.format(totalStats.abortTime / totalStats.aborts));
		}

		if (TUPLE_TIMING) {
			out
					.println("SATIN: TUPLE_SPACE_BCAST_TIME: total "
							+ Timer.format(totalStats.tupleTime)
							+ " time/bcast  "
							+ Timer.format(totalStats.tupleTime
									/ totalStats.tupleMsgs));
			out.println("SATIN: TUPLE_SPACE_WAIT_TIME:  total "
					+ Timer.format(totalStats.tupleWaitTime)
					+ " time/bcast  "
					+ Timer.format(totalStats.tupleWaitTime
							/ totalStats.tupleWaitCount));
			out.println("SATIN: TUPLE_SPACE_ORDER_TIME: total "
					+ Timer.format(totalStats.tupleSeqTime)
					+ " time/bcast  "
					+ Timer.format(totalStats.tupleSeqTime
							/ totalStats.tupleSeqCount));
		}

		if (POLL_FREQ != 0 && POLL_TIMING) {
			out.println("SATIN: POLL_TIME:            total "
					+ Timer.format(totalStats.pollTime) + " time/poll "
					+ Timer.format(totalStats.pollTime / totalStats.pollCount));
		}

		if (GRT_TIMING) {
			out.println("SATIN: GRT_UPDATE_TIME:		  total "
					+ Timer.format(totalStats.tableUpdateTime)
					+ " time/update "
					+ Timer.format(totalStats.tableUpdateTime
							/ totalStats.tableUpdates));
			out.println("SATIN: GRT_LOOKUP_TIME:		  total "
					+ Timer.format(totalStats.tableLookupTime)
					+ " time/lookup "
					+ Timer.format(totalStats.tableLookupTime
							/ totalStats.tableLookups));
			out.println("SATIN: GRT_HANDLE_UPDATE_TIME:	  total "
					+ Timer.format(totalStats.tableHandleUpdateTime)
					+ " time/handle "
					+ Timer.format(totalStats.tableHandleUpdateTime
							/ totalStats.tableUpdates * (size - 1)));
			out.println("SATIN: GRT_HANDLE_LOOKUP_TIME: 	  total "
					+ Timer.format(totalStats.tableHandleLookupTime)
					+ " time/handle "
					+ Timer.format(totalStats.tableHandleLookupTime
							/ totalStats.tableRemoteLookups));
		}

		if (CRASH_TIMING) {
			out.println("SATIN: CRASH_HANDLING_TIME: 	  "
					+ Timer.format(totalStats.crashHandlingTime));
		}

		if (ADD_REPLICA_TIMING) {
			out.println("SATIN: ADD_REPLICA_TIME:	 	  "
					+ Timer.format(totalStats.addReplicaTime));
		}

		out
				.println("-------------------------------SATIN RUN TIME BREAKDOWN------------------------");
		out.println("SATIN: TOTAL_RUN_TIME:                           "
				+ Timer.format(totalTimer.totalTimeVal()));

		double lbTime = (totalStats.stealTime
				- totalStats.invocationRecordReadTime - totalStats.invocationRecordWriteTime)
				/ size;
		if (lbTime < 0.0)
			lbTime = 0.0;
		double lbPerc = lbTime / totalTimer.totalTimeVal() * 100.0;
		double serTime = (totalStats.invocationRecordWriteTime + totalStats.invocationRecordReadTime)
				/ size;
		double serPerc = serTime / totalTimer.totalTimeVal() * 100.0;
		double abortTime = totalStats.abortTime / size;
		double abortPerc = abortTime / totalTimer.totalTimeVal() * 100.0;
		double tupleTime = totalStats.tupleTime / size;
		double tuplePerc = tupleTime / totalTimer.totalTimeVal() * 100.0;
		double tupleWaitTime = totalStats.tupleWaitTime / size;
		double tupleWaitPerc = tupleWaitTime / totalTimer.totalTimeVal()
				* 100.0;
		double tupleSeqTime = totalStats.tupleSeqTime / size;
		double tupleSeqPerc = tupleSeqTime / totalTimer.totalTimeVal() * 100.0;
		double pollTime = totalStats.pollTime / size;
		double pollPerc = pollTime / totalTimer.totalTimeVal() * 100.0;

		double tableUpdateTime = totalStats.tableUpdateTime / size;
		double tableUpdatePerc = tableUpdateTime / totalTimer.totalTimeVal()
				* 100.0;
		double tableLookupTime = totalStats.tableLookupTime / size;
		double tableLookupPerc = tableLookupTime / totalTimer.totalTimeVal()
				* 100.0;
		double tableHandleUpdateTime = totalStats.tableHandleUpdateTime / size;
		double tableHandleUpdatePerc = tableHandleUpdateTime
				/ totalTimer.totalTimeVal() * 100.0;
		double tableHandleLookupTime = totalStats.tableHandleLookupTime / size;
		double tableHandleLookupPerc = tableHandleLookupTime
				/ totalTimer.totalTimeVal() * 100.0;
		double crashHandlingTime = totalStats.crashHandlingTime / size;
		double crashHandlingPerc = crashHandlingTime
				/ totalTimer.totalTimeVal() * 100.0;
		double addReplicaTime = totalStats.addReplicaTime / size;
		double addReplicaPerc = addReplicaTime / totalTimer.totalTimeVal()
				* 100.0;

		double totalOverhead = lbTime + serTime + abortTime + tupleTime
				+ tupleWaitTime + pollTime;
		double totalPerc = totalOverhead / totalTimer.totalTimeVal() * 100.0;
		double appTime = totalTimer.totalTimeVal() - totalOverhead;
		if (appTime < 0.0)
			appTime = 0.0;
		double appPerc = appTime / totalTimer.totalTimeVal() * 100.0;

		if (STEAL_TIMING) {
			out.println("SATIN: LOAD_BALANCING_TIME:     avg. per machine "
					+ Timer.format(lbTime) + " (" + (lbPerc < 10 ? " " : "")
					+ pf.format(lbPerc) + " %)");
			out.println("SATIN: (DE)SERIALIZATION_TIME:  avg. per machine "
					+ Timer.format(serTime) + " (" + (serPerc < 10 ? " " : "")
					+ pf.format(serPerc) + " %)");
		}

		if (ABORT_TIMING) {
			out.println("SATIN: ABORT_TIME:              avg. per machine "
					+ Timer.format(abortTime) + " ("
					+ (abortPerc < 10 ? " " : "") + pf.format(abortPerc)
					+ " %)");
		}

		if (TUPLE_TIMING) {
			out.println("SATIN: TUPLE_SPACE_BCAST_TIME:  avg. per machine "
					+ Timer.format(tupleTime) + " ("
					+ (tuplePerc < 10 ? " " : "") + pf.format(tuplePerc)
					+ " %)");
			out.println("SATIN: TUPLE_SPACE_WAIT_TIME:   avg. per machine "
					+ Timer.format(tupleWaitTime) + " ("
					+ (tupleWaitPerc < 10 ? " " : "")
					+ pf.format(tupleWaitPerc) + " %)");
			out.println("SATIN: TUPLE_SPACE_ORDER_TIME:  avg. per machine "
					+ Timer.format(tupleSeqTime) + " ("
					+ (tupleSeqPerc < 10 ? " " : "") + pf.format(tupleSeqPerc)
					+ " %)");
		}

		if (POLL_FREQ != 0 && POLL_TIMING) {
			out.println("SATIN: POLL_TIME:               avg. per machine "
					+ Timer.format(pollTime) + " ("
					+ (pollPerc < 10 ? " " : "") + pf.format(pollPerc) + " %)");
		}

		if (GRT_TIMING) {
			out
					.println("SATIN: GRT_UPDATE_TIME:              avg. per machine "
							+ Timer.format(tableUpdateTime)
							+ " ("
							+ pf.format(tableUpdatePerc) + " %)");
			out
					.println("SATIN: GRT_LOOKUP_TIME:              avg. per machine "
							+ Timer.format(tableLookupTime)
							+ " ("
							+ pf.format(tableLookupPerc) + " %)");
			out
					.println("SATIN: GRT_HANDLE_UPDATE_TIME:       avg. per machine "
							+ Timer.format(tableHandleUpdateTime)
							+ " ("
							+ pf.format(tableHandleUpdatePerc) + " %)");
			out
					.println("SATIN: GRT_HANDLE_LOOKUP_TIME:       avg. per machine "
							+ Timer.format(tableHandleLookupTime)
							+ " ("
							+ pf.format(tableHandleLookupPerc) + " %)");
		}

		if (CRASH_TIMING) {
			out.println("SATIN: CRASH_HANDLING_TIME: 	  avg. per machine "
					+ Timer.format(crashHandlingTime) + " ("
					+ pf.format(crashHandlingPerc) + " %)");
		}

		if (ADD_REPLICA_TIMING) {
			out.println("SATIN: ADD_REPLICA_TIME:	 	  avg. per machine "
					+ Timer.format(addReplicaTime) + " ("
					+ pf.format(addReplicaPerc) + " %)");
		}

		out.println("\nSATIN: TOTAL_PARALLEL_OVERHEAD: avg. per machine "
				+ Timer.format(totalOverhead) + " ("
				+ (totalPerc < 10 ? " " : "") + pf.format(totalPerc) + " %)");

		out.println("SATIN: USEFUL_APP_TIME:         avg. per machine "
				+ Timer.format(appTime) + " (" + (appPerc < 10 ? " " : "")
				+ pf.format(appPerc) + " %)");

	}

	protected void printDetailedStats() {
		java.text.NumberFormat nf = java.text.NumberFormat.getInstance();

		if (SPAWN_STATS) {
			out.println("SATIN '" + ident.name() + "': SPAWN_STATS: spawns = "
					+ spawns + " executed = " + jobsExecuted + " syncs = "
					+ syncs);
			if (ABORTS) {
				out.println("SATIN '" + ident.name()
						+ "': ABORT_STATS 1: aborts = " + aborts
						+ " abort msgs = " + abortMessages + " aborted jobs = "
						+ abortedJobs);
			}
		}
		if (TUPLE_STATS) {
			out.println("SATIN '" + ident.name()
					+ "': TUPLE_STATS 1: tuple bcast msgs: " + tupleMsgs
					+ ", bytes = " + nf.format(tupleBytes));
		}
		if (STEAL_STATS) {
			out.println("SATIN '" + ident.name()
					+ "': INTRA_STATS: messages = " + intraClusterMessages
					+ ", bytes = " + nf.format(intraClusterBytes));

			out.println("SATIN '" + ident.name()
					+ "': INTER_STATS: messages = " + interClusterMessages
					+ ", bytes = " + nf.format(interClusterBytes));

			out
					.println("SATIN '" + ident.name()
							+ "': STEAL_STATS 1: attempts = " + stealAttempts
							+ " success = " + stealSuccess + " ("
							+ (((double) stealSuccess / stealAttempts) * 100.0)
							+ " %)");

			out.println("SATIN '" + ident.name()
					+ "': STEAL_STATS 2: requests = " + stealRequests
					+ " jobs stolen = " + stolenJobs);

			if (STEAL_TIMING) {
				out.println("SATIN '" + ident.name()
						+ "': STEAL_STATS 3: attempts = "
						+ stealTimer.nrTimes() + " total time = "
						+ stealTimer.totalTime() + " avg time = "
						+ stealTimer.averageTime());

				out.println("SATIN '" + ident.name()
						+ "': STEAL_STATS 4: handleSteals = "
						+ handleStealTimer.nrTimes() + " total time = "
						+ handleStealTimer.totalTime() + " avg time = "
						+ handleStealTimer.averageTime());
				out.println("SATIN '" + ident.name()
						+ "': STEAL_STATS 5: invocationRecordWrites = "
						+ invocationRecordWriteTimer.nrTimes()
						+ " total time = "
						+ invocationRecordWriteTimer.totalTime()
						+ " avg time = "
						+ invocationRecordWriteTimer.averageTime());
				out.println("SATIN '" + ident.name()
						+ "': STEAL_STATS 6: invocationRecordReads = "
						+ invocationRecordReadTimer.nrTimes()
						+ " total time = "
						+ invocationRecordReadTimer.totalTime()
						+ " avg time = "
						+ invocationRecordReadTimer.averageTime());
			}

			if (ABORTS && ABORT_TIMING) {
				out.println("SATIN '" + ident.name()
						+ "': ABORT_STATS 2: aborts = " + abortTimer.nrTimes()
						+ " total time = " + abortTimer.totalTime()
						+ " avg time = " + abortTimer.averageTime());
			}

			if (IDLE_TIMING) {
				out.println("SATIN '" + ident.name()
						+ "': IDLE_STATS: idle count = " + idleTimer.nrTimes()
						+ " total time = " + idleTimer.totalTime()
						+ " avg time = " + idleTimer.averageTime());
			}

			if (POLL_FREQ != 0 && POLL_TIMING) {
				out.println("SATIN '" + ident.name()
						+ "': POLL_STATS: poll count = " + pollTimer.nrTimes()
						+ " total time = " + pollTimer.totalTime()
						+ " avg time = " + pollTimer.averageTime());
			}

			if (STEAL_TIMING && IDLE_TIMING) {
				out.println("SATIN '"
						+ ident.name()
						+ "': COMM_STATS: software comm time = "
						+ Timer.format(stealTimer.totalTimeVal()
								+ handleStealTimer.totalTimeVal()
								- idleTimer.totalTimeVal()));
			}

			if (TUPLE_TIMING) {
				out.println("SATIN '" + ident.name()
						+ "': TUPLE_STATS 2: bcasts = " + tupleTimer.nrTimes()
						+ " total time = " + tupleTimer.totalTime()
						+ " avg time = " + tupleTimer.averageTime());

				out.println("SATIN '" + ident.name()
						+ "': TUPLE_STATS 3: waits = "
						+ tupleOrderingWaitTimer.nrTimes() + " total time = "
						+ tupleOrderingWaitTimer.totalTime() + " avg time = "
						+ tupleOrderingWaitTimer.averageTime());

				out.println("SATIN '" + ident.name()
						+ "': TUPLE_STATS 4: sequencer accesses = "
						+ tupleOrderingSeqTimer.nrTimes() + " total time = "
						+ tupleOrderingSeqTimer.totalTime() + " avg time = "
						+ tupleOrderingSeqTimer.averageTime());
			}
			algorithm.printStats(out);
		}

		if (FAULT_TOLERANCE) {
			if (GRT_STATS) {
				out.println("SATIN '" + ident.name() + "': "
						+ globalResultTable.numUpdates
						+ " updates of the table.");
				out.println("SATIN '" + ident.name() + "': "
						+ globalResultTable.numLookupsSucceded
						+ " lookups succeded, of which:");
				out.println("SATIN '" + ident.name() + "': "
						+ globalResultTable.numRemoteLookups
						+ " remote lookups.");
				out.println("SATIN '" + ident.name() + "': "
						+ globalResultTable.maxNumEntries
						+ " entries maximally.");
			}
			if (GRT_TIMING) {
				out.println("SATIN '" + ident.name() + "': "
						+ lookupTimer.totalTime() + " spent in lookups");
				out.println("SATIN '" + ident.name() + "': "
						+ lookupTimer.averageTime() + " per lookup");
				out.println("SATIN '" + ident.name() + "': "
						+ updateTimer.totalTime() + " spent in updates");
				out.println("SATIN '" + ident.name() + "': "
						+ updateTimer.averageTime() + " per update");
				out.println("SATIN '" + ident.name() + "': "
						+ handleUpdateTimer.totalTime()
						+ " spent in handling updates");
				out.println("SATIN '" + ident.name() + "': "
						+ handleUpdateTimer.averageTime()
						+ " per update handle");
				out.println("SATIN '" + ident.name() + "': "
						+ handleLookupTimer.totalTime()
						+ " spent in handling lookups");
				out.println("SATIN '" + ident.name() + "': "
						+ handleLookupTimer.averageTime()
						+ " per lookup handle");

			}
			if (CRASH_TIMING) {
				out
						.println("SATIN '" + ident.name() + "': "
								+ crashTimer.totalTime()
								+ " spent in handling crashes");
			}
			if (TABLE_CHECK_TIMING) {
				out.println("SATIN '" + ident.name() + "': "
						+ redoTimer.totalTime() + " spent in redoing");

			}

			if (FT_ABORT_STATS) {
				out.println("SATIN '" + ident.name() + "': " + killedOrphans
						+ " orphans killed");
			}
		}
	}

}
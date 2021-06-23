package com.avito.android.runner.report.internal

import com.avito.android.runner.report.Report
import com.avito.android.runner.report.TestAttempt
import com.avito.logger.LoggerFactory
import com.avito.logger.create
import com.avito.report.model.AndroidTest
import com.avito.report.model.TestStaticData
import com.avito.test.model.DeviceName
import com.avito.test.model.TestName
import com.avito.time.TimeProvider

internal class InMemoryReport(
    private val timeProvider: TimeProvider,
    loggerFactory: LoggerFactory,
    private val testAttemptsAggregateStrategy: TestAttemptsAggregateStrategy
) : Report {

    private val logger = loggerFactory.create<InMemoryReport>()

    private val testAttempts: MutableList<TestAttempt> = mutableListOf()

    @Synchronized
    override fun addTest(testAttempt: TestAttempt) {
        logger.debug("addTest $testAttempt")
        this.testAttempts.add(testAttempt)
    }

    @Synchronized
    override fun addSkippedTests(skippedTests: List<Pair<TestStaticData, String>>) {
        logger.debug("addSkippedTests $skippedTests")

        this.testAttempts.addAll(
            skippedTests.map { (test, reason) ->
                TestAttempt.createWithoutExecution(
                    AndroidTest.Skipped.fromTestMetadata(
                        testStaticData = test,
                        skipReason = reason,
                        reportTime = timeProvider.nowInSeconds()
                    )
                )
            }
        )
    }

    @Synchronized
    override fun getTestResults(): Collection<AndroidTest> {
        val grouped: Map<TestKey, List<TestAttempt>> =
            testAttempts.groupBy(
                keySelector = {
                    TestKey(
                        testName = it.testResult.name,
                        deviceName = it.testResult.device
                    )
                }
            )

        return grouped.mapValues { (_, executions) ->
            testAttemptsAggregateStrategy.getTestResult(executions)
        }.values
    }

    private data class TestKey(val testName: TestName, val deviceName: DeviceName)
}

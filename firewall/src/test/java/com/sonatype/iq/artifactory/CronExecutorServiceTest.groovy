/*
 * Copyright 2019, Sonatype, Inc.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.sonatype.iq.artifactory

import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

import org.joda.time.DateTime
import org.joda.time.Seconds
import org.slf4j.Logger
import spock.lang.Specification
import spock.lang.Unroll

class CronExecutorServiceTest
    extends Specification
{
  Runnable runnable = Mock()

  Logger log = Mock()

  CronExecutorService cron = new CronExecutorService(0)

  def 'Schedules repeated executions'() {
    when: 'cron job is scheduled to run every second and we wait for 3.5 seconds'
      cron.scheduleWithCronExpression(runnable, '* * * * * ? *', log)
      sleep(3_500)

    then: 'runnable is run 3 times'
      3 * runnable.run()
  }

  def 'Throws exception if the expression is invalid'() {
    when:
      cron.scheduleWithCronExpression(runnable, '0 0 0 ? * * 2422', log) // in the year 2422

    then:
      IllegalArgumentException thrown = thrown()
      thrown.message == "Failed to parse cron expression. Value 2422 not in range [1970, 2099]"
  }

  @Unroll
  def 'calculates time to next execution correctly'() {
    given:
      ScheduledThreadPoolExecutor mockScheduler = Mock()
      ZonedDateTime assumedDateTime = ZonedDateTime.parse(dateTime)

    when:
      cron.delegate = mockScheduler
      cron.scheduleWithCronExpression(runnable, expression, assumedDateTime)

    then:
      1 * mockScheduler.schedule(runnable, { it == nextExectionInSeconds }, TimeUnit.SECONDS)

    where:
      dateTime               | expression         | nextExectionInSeconds
      '2021-02-05T20:43:09Z' | '0 0 0 ? * * 2050' | 912050211
      '2021-02-05T20:43:09Z' | '* * * * * ? *'    | 1
      '2021-02-05T20:00:00Z' | '* 1 * * * ? *'    | 60 // one minute
      '2021-02-05T20:00:00Z' | '* * 21 * * ? *'   | 60 * 60 // one hour
      '2021-02-05T20:00:00Z' | '* * 20 6 * ? *'   | 60 * 60 * 24 // one day
  }
}

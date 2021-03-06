package com.socrata.balboa.server

import java.net.URL

import com.fasterxml.jackson.core.JsonParseException
import com.socrata.balboa.metrics.Metric.RecordType
import com.stackmob.newman.ApacheHttpClient
import com.stackmob.newman.dsl.{GET, POST}
import com.stackmob.newman.response.{HttpResponse, HttpResponseCode}
import com.stackmob.newman.response.HttpResponseCode.{BadRequest, NoContent, NotFound, Ok}
import org.json4s._
import org.json4s.jackson.JsonMethods.{parse, pretty, render}
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}

import scala.collection.mutable
import scala.concurrent.Await
import scala.util.Try

class MetricsIntegrationTest extends FlatSpec with Matchers with BeforeAndAfterEach {
  implicit val httpClient = new ApacheHttpClient
  val testMetricPrefix = "testMetric"
  val testEntityPrefix = "testMetricsEntity"
  var testEntityName = ""
  var testMetricName = ""
  val testStart = "1969-12-01"
  val testPersistedDate = "1970-01-01"
  val testPersistedDateEpoch = ServiceUtils.parseDate(testPersistedDate).get.getTime
  val testEnd = "1970-02-02"
  val protobuf = "application/x-protobuf"

  protected implicit val jsonFormats: Formats = DefaultFormats

  import scala.language.implicitConversions
  class AssertionJSON(actual: => String) {
      def shouldBeJSON(expected: String) = {
        val actualObj = Try(parse(actual)).recover({ case jpe: JsonParseException =>
                          fail(s"""Unable to parse actual value "$actual" as JSON""", jpe)}).get
        val expectObj = Try(parse(expected)).recover({ case jpe: JsonParseException =>
                          fail(s"""Unable to parse expected value "$expected" as JSON""", jpe)}).get

        withClue(
          "\nTextual actual:\n\n" + pretty(render(actualObj)) + "\n\n\n" +
          "Textual expected:\n\n" + pretty(render(expectObj)) + "\n\n")
          { actualObj should be (expectObj) }
      }
  }
  implicit def convertJSONAssertion(j: => String): AssertionJSON = new AssertionJSON(j)

  case class JSONAndProtoResponse(json: HttpResponse, proto: HttpResponse) {
    def shouldHaveCode(code: HttpResponseCode) = {
      json.code.code should be (code.code)
      proto.code.code should be (code.code)
    }
  }

  def getJSONResponse(url: String): HttpResponse = {
    Await.result(GET(new URL(Config.Server, url)).apply, Config.RequestTimeout)
  }

  def getJSONProtoResponse(url: String): JSONAndProtoResponse = {
    JSONAndProtoResponse (
      getJSONResponse(url),
      Await.result(GET(new URL(Config.Server, url)).setHeaders(("Accept", protobuf)).apply, Config.RequestTimeout)
    )
  }

  // Ensures each test interacts with a unique entity
  override def beforeEach() = {
    val uuid = java.util.UUID.randomUUID().toString
    testEntityName = testEntityPrefix + "-" + uuid
    testMetricName = testMetricPrefix + "-" + uuid
  }

  // Note: the following three endpoints probably should not exist. All
  // socrata-http url patterns match urls with extra segments. This is
  // preserved for the purposes of compatibility while transitioning to
  // Scalatra. Once that transition is complete, an analysis of logs should
  // determine if this is in use anywhere and it can be removed. Proper
  // response for this endpoint should be a 404.
  "Retrieve /metrics/*/range/*" should "show the same results as /metrics/*/range" in {
    persistManyMetrics(testEntityName, 10, RecordType.ABSOLUTE)
    val rangeResponse = getJSONResponse(s"metrics/$testEntityName/range?start=$testStart&end=$testEnd")
    val rangeWithExtraResponse = getJSONResponse(s"metrics/$testEntityName/range/whatever?start=$testStart&end=$testEnd")
    rangeWithExtraResponse.bodyString shouldBeJSON rangeResponse.bodyString
  }
  "Retrieve /metrics/*/series/*" should "show the same results as /metrics/*/series" in {
    persistManyMetrics(testEntityName, 10, RecordType.ABSOLUTE)
    val seriesResponse = getJSONResponse(s"metrics/$testEntityName/series?period=MONTHLY&start=$testStart&end=$testEnd")
    val seriesWithExtraResponse = getJSONResponse(s"metrics/$testEntityName/series/whatever?period=MONTHLY&start=$testStart&end=$testEnd")
    seriesWithExtraResponse.bodyString shouldBeJSON seriesResponse.bodyString
  }
  "Retrieve /metrics/*/whatever (not /range or /series)" should "show the same results as /metrics/*" in {
    persistSingleMetric()
    val metricResponse = getJSONResponse(s"/metrics/$testEntityName?period=YEARLY&date=$testStart")
    val metricWithExtraResponse = getJSONResponse(s"/metrics/$testEntityName/whatever?period=YEARLY&date=$testStart")
    metricWithExtraResponse.bodyString shouldBeJSON metricResponse.bodyString
  }

  "Retrieve /metrics range with no range" should "be fail" in {
    val response = getJSONProtoResponse(s"/metrics/$testEntityName/range")
    response shouldHaveCode BadRequest
  }

  "Retrieve /metrics without specifying" should "be not found" in {
    val response = getJSONProtoResponse("/metrics")
    response shouldHaveCode NotFound
  }

  "Retrieve /metrics range with a range" should "succeed" in {
    val response = getJSONProtoResponse(s"/metrics/$testEntityName/range?start=$testStart&end=$testEnd")
    response shouldHaveCode Ok
    response.json.bodyString shouldBeJSON """{}"""
  }

  "Retrieve /metrics/* with no period" should "fail with error msg" in {
    val response = getJSONProtoResponse(s"/metrics/$testEntityName")
    response shouldHaveCode BadRequest
    response.json.bodyString shouldBeJSON """ { "error": 400, "message": "Parameter period required." } """
  }

  "Retrieve /metrics/* with bad period" should "fail with error msg" in {
    val response = getJSONProtoResponse(s"/metrics/$testEntityName?period=crud")
    response shouldHaveCode BadRequest
    response.json.bodyString shouldBeJSON """ { "error": 400, "message": "Unable to parse period : No period named crud" } """
  }

  "Retrieve /metrics/* with no date" should "fail with error msg" in {
    val response = getJSONProtoResponse(s"/metrics/$testEntityName?period=YEARLY")
    response shouldHaveCode BadRequest
    response.json.bodyString shouldBeJSON """ { "error": 400, "message": "Parameter date required." } """
  }

  "Retrieve /metrics/* with bad date" should "fail with error msg" in {
    val response = getJSONProtoResponse(s"/metrics/$testEntityName?period=YEARLY&date=crud")
    response shouldHaveCode BadRequest
    response.json.bodyString shouldBeJSON """ { "error": 400, "message": "Unable to parse date crud" } """
  }

  "Retrieve /metrics/* with valid period and date" should "succeed" in {
    val response = getJSONProtoResponse(s"/metrics/$testEntityName?period=YEARLY&date=$testStart")
    response shouldHaveCode Ok
    response.json.bodyString shouldBeJSON """{}"""
  }

  "Retrieve /metrics/*/series with no period" should "fail with error msg" in {
    val response = getJSONProtoResponse(s"/metrics/$testEntityName/range")
    response shouldHaveCode BadRequest
  }

  "Retrieve /metrics/*/series with no start" should "fail with error msg" in {
    val response = getJSONResponse(s"/metrics/$testEntityName/series?period=YEARLY")
    response.code.code should be (BadRequest.code)
    response.bodyString shouldBeJSON """ { "error": 400, "message": "Parameter start required." } """
  }

  "Retrieve /metrics/*/series with no end" should "fail with error msg" in {
    val response = getJSONResponse(s"/metrics/$testEntityName/series?period=YEARLY&start=$testStart")
    response.code.code should be (BadRequest.code)
    response.bodyString shouldBeJSON """ { "error": 400, "message": "Parameter end required." } """
  }

  "Retrieve /metrics/*/series with period, start, and end" should "succeed" in {
    val response = getJSONResponse(s"/metrics/$testEntityName/series?period=YEARLY&start=$testStart&end=$testEnd")
    response.code.code should be (Ok.code)
  }

  "Retrieve /metrics/*/range with no start" should "fail with error msg" in {
    val response = getJSONProtoResponse(s"/metrics/$testEntityName/range")
    response shouldHaveCode BadRequest
    response.json.bodyString shouldBeJSON """ { "error": 400, "message": "Parameter start required." } """
  }

  "Retrieve /metrics/*/range with no end" should "fail with error msg" in {
    val response = getJSONProtoResponse(s"/metrics/$testEntityName/range?start=$testStart")
    response shouldHaveCode BadRequest
    response.json.bodyString shouldBeJSON """ { "error": 400, "message": "Parameter end required." } """
  }

  "Retrieve /metrics/*/range with start and end" should "succeed" in {
    val response = getJSONProtoResponse(s"/metrics/$testEntityName/range?start=$testStart&end=$testEnd")
    response shouldHaveCode Ok
  }

  // Returns the JSON string representation of the metric added
  def persistSingleMetric(entityId: String = testEntityName,
                          metricType: RecordType = RecordType.ABSOLUTE): String = {
    val url = new URL(Config.Server, s"/metrics/$entityId")
    val result = Await.result(
      POST(url).setBody(
        pretty(render(Extraction.decompose(
          EntityJSON(
            testPersistedDateEpoch,
            Map(testMetricName -> MetricJSON(1, metricType.toString.toUpperCase)))))))
      .apply,
      Config.RequestTimeout)

    result.code.code should be (NoContent.code)

    s"""{ "$testMetricName": { "value": 1, "type": "${metricType.toString}" } }"""
  }

  // Returns the JSON string representation of the metrics added
  def persistManyMetrics(entityId: String, count: Int, metricTypes: RecordType*): String = {
    val url = new URL(Config.Server, s"/metrics/$entityId")
    val metRange = 0 to count

    val metrics = new mutable.HashMap[String, MetricJSON]
    for ((metricType, typeIndex) <- metricTypes.zipWithIndex) {
      for (i <- metRange) {
        val testNum = (typeIndex * (count + 1)) + i
        metrics.put(testMetricName + "-" + testNum, new MetricJSON(1, metricType.toString.toUpperCase))
      }
    }

    val result = Await.result(
      POST(url).setBody(
        pretty(render(Extraction.decompose(
          EntityJSON(
            testPersistedDateEpoch,
            metrics.toMap)))))
      .apply,
      Config.RequestTimeout
    )

    result.code.code should be (NoContent.code)

    "{" +
      metricTypes.zipWithIndex.flatMap({
        case (metricType, typeIndex) =>
          metRange.map(i =>
            s""" "$testMetricName-${(typeIndex * (count + 1)) + i}": { "value": 1, "type": "${metricType.toString}" } """
      )}).mkString(", ") +
      "}"
  }

  "Persist absolute metrics via POST" should "succeed" in {
    persistManyMetrics(testEntityName, 10, RecordType.ABSOLUTE)
  }

  "Persist aggregate metrics via POST" should "succeed" in {
    persistManyMetrics(testEntityName, 10, RecordType.AGGREGATE)
  }

  "Persist both absolute and aggregate metrics in the same request" should "succeed" in {
    persistManyMetrics(testEntityName, 10, RecordType.ABSOLUTE, RecordType.AGGREGATE)
  }

  "Retrieve /metrics/* after persisting" should "show persisted metric" in {
    val expected = persistSingleMetric()
    val response = getJSONProtoResponse(s"/metrics/$testEntityName?period=YEARLY&date=$testPersistedDate")
    response shouldHaveCode Ok
    response.json.bodyString shouldBeJSON expected
  }

  "Retrieve /metrics/* after persisting many absolute and aggregate metrics" should "show persisted metrics" in {
    val expected = persistManyMetrics(testEntityName, 10, RecordType.ABSOLUTE, RecordType.AGGREGATE)
    val response = getJSONProtoResponse(s"/metrics/$testEntityName?period=YEARLY&date=$testPersistedDate")
    response shouldHaveCode Ok
    response.json.bodyString shouldBeJSON expected
  }

  "Retrieve /metrics range after persisting" should "show persisted metrics" in {
    val expected = persistSingleMetric()
    val response = getJSONProtoResponse(s"metrics/$testEntityName/range?start=$testStart&end=$testEnd")
    response shouldHaveCode Ok
    response.json.bodyString shouldBeJSON expected
  }

  "Retrieve /metrics ranges after persisting" should "show persisted metrics" in {
    val expected = s"""{ "$testEntityName": ${persistSingleMetric()} }"""
    val response = getJSONResponse(s"metrics/range?entityId=$testEntityName&start=$testStart&end=$testEnd")
    response.code shouldBe Ok
    response.bodyString shouldBeJSON expected
  }

  "Retrieve /metrics range after persisting multiple metrics" should "show multiple persisted metrics" in {
    val expected = persistManyMetrics(testEntityName, 10, RecordType.ABSOLUTE)
    val response = getJSONProtoResponse(s"metrics/$testEntityName/range?start=$testStart&end=$testEnd")
    response shouldHaveCode Ok
    response.json.bodyString shouldBeJSON expected
  }

  "Retrieve /metrics ranges after persisting multiple entities" should "show persisted metrics" in {
    val entity1 = testEntityName + "-1"
    val entity2 = testEntityName + "-2"
    val expected = s"""{ "$entity1": ${persistSingleMetric(entity1)}, "$entity2": ${persistSingleMetric(entity2)} }"""
    val response = getJSONResponse(s"metrics/range?entityId=$entity1&entityId=$entity2&start=$testStart&end=$testEnd")
    response.code shouldBe Ok
    response.bodyString shouldBeJSON expected
  }

  "Retrieve /metrics series after persisting" should "show persisted metrics" in {
    val expectedMetric = persistSingleMetric()

    val response = getJSONResponse(s"metrics/$testEntityName/series?period=MONTHLY&start=$testStart&end=$testEnd")
    response.code.code should be (Ok.code)

    val expectSeries1 = """ { "start" : -2678400000, "end" : -1, "metrics" : { } } """
    val expectSeries2 = """ { "start" : 0, "end" : 2678399999, "metrics" : %s } """.format(expectedMetric)
    val expectSeries3 = """ { "start" : 2678400000, "end": 5097599999, "metrics" : { } } """
    val expected = s"[ $expectSeries1, $expectSeries2, $expectSeries3 ]"
    response.bodyString shouldBeJSON expected
  }

  def persistMultipleMetricSeries(entityId: String = testEntityName): String = {
    val expectedMetrics = persistManyMetrics(entityId, 10, RecordType.ABSOLUTE)

    val expectSeries1 = """ { "start" : -2678400000, "end" : -1, "metrics" : { } } """
    val expectSeries2 = """ { "start" : 0, "end" : 2678399999, "metrics" : %s } """.format(expectedMetrics)
    val expectSeries3 = """ { "start" : 2678400000, "end": 5097599999, "metrics" : { } } """
    val expected = s"[ $expectSeries1, $expectSeries2, $expectSeries3 ]"
    expected
  }

  "Retrieve /metrics series after persisting multiple metrics" should "show persisted metrics" in {
    val expected = persistMultipleMetricSeries()
    val response = getJSONResponse(s"metrics/$testEntityName/series?period=MONTHLY&start=$testStart&end=$testEnd")
    response.code.code should be (Ok.code)
    response.bodyString shouldBeJSON expected
  }

  "Retrieve /metrics serieses after persisting multiple entities" should "show persisted metrics" in {
    val entity1 = testEntityName + "-1"
    val entity2 = testEntityName + "-2"
    val expected =
      s"""{ "$entity1": ${persistMultipleMetricSeries(entity1)}, "$entity2": ${persistMultipleMetricSeries(entity2)} }"""
    val response = getJSONResponse(s"metrics/series?entityId=$entity1&entityId=$entity2&" +
      s"period=MONTHLY&start=$testStart&end=$testEnd"
    )
    response.code.code should be (Ok.code)
    response.bodyString shouldBeJSON expected
  }
}

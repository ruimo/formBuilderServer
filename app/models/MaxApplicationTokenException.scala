package models

class MaxApplicationTokenException(
  val maxTokenCount: Int, cause: Throwable
) extends Exception(
  "Max application token count exceeded (=" + maxTokenCount + ")", cause
) {
  def this(maxTokenCount: Int) = this(maxTokenCount, null)
}

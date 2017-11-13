package models

import java.sql.SQLException
import play.Logger

object ExceptionMapper {
  def mapException[T](block: => T): T = try {
    block
  }
  catch {
    case e: SQLException => {
      val className = e.getClass.getName

      Logger.error("SQLState: " + e.getSQLState())
      className match {
        case "org.postgresql.util.PSQLException" => e.getSQLState() match {
          case "23505" => throw new UniqueConstraintException(e)
          case _ => throw e
        }
        case "org.h2.jdbc.JdbcSQLException" => e.getSQLState() match {
          case "23505" => throw new UniqueConstraintException(e)
          case _ => throw e
        }

        case _ => throw e
      }
    }
    case e: Throwable => throw e
  }
}

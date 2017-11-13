package models

import java.time.LocalDate

case class CreateContractedUser(
  planId: Long,
  userId: Long,
  contractFrom: LocalDate,
  contractTo: LocalDate
)

package models

import java.sql.Connection

case class CreateContractPlan(
  planName: String,
  maxFormFormat: Int,
  deprecated: Boolean
) {
  def save()(implicit conn: Connection, contractPlanRepo: ContractPlanRepo) {
    contractPlanRepo.create(planName, maxFormFormat)
  }

  def update(id: ContractPlanId)(implicit conn: Connection, contractPlanRepo: ContractPlanRepo) {
    contractPlanRepo.update(id, planName, maxFormFormat, deprecated)
  }
}

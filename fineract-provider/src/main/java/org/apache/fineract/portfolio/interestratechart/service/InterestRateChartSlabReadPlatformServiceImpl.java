/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.portfolio.interestratechart.service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.fineract.infrastructure.codes.data.CodeValueData;
import org.apache.fineract.infrastructure.codes.service.CodeValueReadPlatformService;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.infrastructure.core.service.RoutingDataSource;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.monetary.data.CurrencyData;
import org.apache.fineract.portfolio.client.api.ClientApiConstants;
import org.apache.fineract.portfolio.common.service.CommonEnumerations;
import org.apache.fineract.portfolio.interestratechart.data.InterestIncentiveData;
import org.apache.fineract.portfolio.interestratechart.data.InterestRateChartSlabData;
import org.apache.fineract.portfolio.interestratechart.exception.InterestRateChartSlabNotFoundException;
import org.apache.fineract.portfolio.interestratechart.incentive.InterestIncentiveAttributeName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
public class InterestRateChartSlabReadPlatformServiceImpl implements InterestRateChartSlabReadPlatformService {

    private final PlatformSecurityContext context;
    private final JdbcTemplate jdbcTemplate;
    private final InterestRateChartSlabExtractor chartSlabExtractor = new InterestRateChartSlabExtractor();
    private final InterestRateChartDropdownReadPlatformService chartDropdownReadPlatformService;
    private final InterestIncentiveDropdownReadPlatformService interestIncentiveDropdownReadPlatformService;
    private final CodeValueReadPlatformService codeValueReadPlatformService;

    @Autowired
    public InterestRateChartSlabReadPlatformServiceImpl(PlatformSecurityContext context, final RoutingDataSource dataSource,
            InterestRateChartDropdownReadPlatformService chartDropdownReadPlatformService,
            final InterestIncentiveDropdownReadPlatformService interestIncentiveDropdownReadPlatformService,
            final CodeValueReadPlatformService codeValueReadPlatformService) {
        this.context = context;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.chartDropdownReadPlatformService = chartDropdownReadPlatformService;
        this.interestIncentiveDropdownReadPlatformService = interestIncentiveDropdownReadPlatformService;
        this.codeValueReadPlatformService = codeValueReadPlatformService;
    }

    @Override
    public Collection<InterestRateChartSlabData> retrieveAll(Long chartId) {
        this.context.authenticatedUser();
        final String sql = "select " + this.chartSlabExtractor.schema() + " where ircd.interest_rate_chart_id = ? order by ircd.id";
        return this.jdbcTemplate.query(sql, this.chartSlabExtractor, new Object[] { chartId });
    }

    @Override
    public InterestRateChartSlabData retrieveOne(Long chartId, Long chartSlabId) {
        this.context.authenticatedUser();
        final String sql = "select " + this.chartSlabExtractor.schema() + " where irc.id = ? order by ircd.id asc";
        Collection<InterestRateChartSlabData> chartDatas = this.jdbcTemplate.query(sql, this.chartSlabExtractor,
                new Object[] { chartSlabId, chartId });
        if (chartDatas == null || chartDatas.isEmpty()) { throw new InterestRateChartSlabNotFoundException(chartSlabId, chartId); }

        return chartDatas.iterator().next();
    }

    @Override
    public InterestRateChartSlabData retrieveWithTemplate(InterestRateChartSlabData chartSlab) {
        final List<CodeValueData> genderOptions = new ArrayList<>(
                this.codeValueReadPlatformService.retrieveCodeValuesByCode(ClientApiConstants.GENDER));

        final List<CodeValueData> clientTypeOptions = new ArrayList<>(
                this.codeValueReadPlatformService.retrieveCodeValuesByCode(ClientApiConstants.CLIENT_TYPE));

        final List<CodeValueData> clientClassificationOptions = new ArrayList<>(
                this.codeValueReadPlatformService.retrieveCodeValuesByCode(ClientApiConstants.CLIENT_CLASSIFICATION));

        return InterestRateChartSlabData.withTemplate(chartSlab, this.chartDropdownReadPlatformService.retrievePeriodTypeOptions(),
                this.interestIncentiveDropdownReadPlatformService.retrieveEntityTypeOptions(),
                this.interestIncentiveDropdownReadPlatformService.retrieveAttributeNameOptions(),
                this.interestIncentiveDropdownReadPlatformService.retrieveConditionTypeOptions(),
                this.interestIncentiveDropdownReadPlatformService.retrieveIncentiveTypeOptions(), genderOptions, clientTypeOptions,
                clientClassificationOptions);
    }

    @Override
    public InterestRateChartSlabData retrieveTemplate() {
        final List<CodeValueData> genderOptions = new ArrayList<>(
                this.codeValueReadPlatformService.retrieveCodeValuesByCode(ClientApiConstants.GENDER));

        final List<CodeValueData> clientTypeOptions = new ArrayList<>(
                this.codeValueReadPlatformService.retrieveCodeValuesByCode(ClientApiConstants.CLIENT_TYPE));

        final List<CodeValueData> clientClassificationOptions = new ArrayList<>(
                this.codeValueReadPlatformService.retrieveCodeValuesByCode(ClientApiConstants.CLIENT_CLASSIFICATION));
        return InterestRateChartSlabData.template(this.chartDropdownReadPlatformService.retrievePeriodTypeOptions(),
                this.interestIncentiveDropdownReadPlatformService.retrieveEntityTypeOptions(),
                this.interestIncentiveDropdownReadPlatformService.retrieveAttributeNameOptions(),
                this.interestIncentiveDropdownReadPlatformService.retrieveConditionTypeOptions(),
                this.interestIncentiveDropdownReadPlatformService.retrieveIncentiveTypeOptions(), genderOptions, clientTypeOptions,
                clientClassificationOptions);
    }

    private static final class InterestRateChartSlabsMapper implements RowMapper<InterestRateChartSlabData> {

        private final String schemaSql;

        public String schema() {
            return this.schemaSql;
        }

        private InterestRateChartSlabsMapper() {
            final StringBuilder sqlBuilder = new StringBuilder(400);

            sqlBuilder.append("ircd.id as ircdId, ircd.description as ircdDescription, ircd.period_type_enum ircdPeriodTypeId, ").append(
                    "ircd.from_period as ircdFromPeriod, ircd.to_period as ircdToPeriod, ircd.amount_range_from as ircdAmountRangeFrom, ")
                    .append("ircd.amount_range_to as ircdAmountRangeTo, ircd.annual_interest_rate as ircdAnnualInterestRate, ")
                    .append("curr.code as currencyCode, curr.name as currencyName, curr.internationalized_name_code as currencyNameCode, ")
                    .append("curr.display_symbol as currencyDisplaySymbol, curr.decimal_places as currencyDigits, curr.currency_multiplesof as inMultiplesOf, ")
                    .append("iri.id as iriId, ").append(" iri.entiry_type as entityType, iri.attribute_name as attributeName ,")
                    .append(" iri.condition_type as conditionType, iri.attribute_value as attributeValue, ")
                    .append(" iri.incentive_type as incentiveType, iri.amount as amount, ").append("code.code_value as attributeValueDesc ")
                    .append("from ").append("m_interest_rate_slab ircd ")
                    .append(" left join m_interest_incentives iri on iri.interest_rate_slab_id = ircd.id ")
                    .append(" left join m_code_value code on code.id = iri.attribute_value ")
                    .append("left join m_currency curr on ircd.currency_code= curr.code ");
            this.schemaSql = sqlBuilder.toString();
        }

        @Override
        public InterestRateChartSlabData mapRow(ResultSet rs, @SuppressWarnings("unused") int rowNum) throws SQLException {
            final Long id = JdbcSupport.getLongDefaultToNullIfZero(rs, "ircdId");
            // If there are not chart Slabs are associated then in
            // InterestRateChartExtractor the chart Slabs id will be null.
            if (id == null) { return null; }

            final String description = rs.getString("ircdDescription");
            final Integer fromPeriod = JdbcSupport.getInteger(rs, "ircdFromPeriod");
            final Integer toPeriod = JdbcSupport.getInteger(rs, "ircdToPeriod");
            final Integer periodTypeId = JdbcSupport.getInteger(rs, "ircdPeriodTypeId");
            final EnumOptionData periodType = InterestRateChartEnumerations.periodType(periodTypeId);
            final BigDecimal amountRangeFrom = rs.getBigDecimal("ircdAmountRangeFrom");
            final BigDecimal amountRangeTo = rs.getBigDecimal("ircdAmountRangeTo");
            final BigDecimal annualInterestRate = rs.getBigDecimal("ircdAnnualInterestRate");

            // currency Slabs
            final String currencyCode = rs.getString("currencyCode");
            final String currencyName = rs.getString("currencyName");
            final String currencyNameCode = rs.getString("currencyNameCode");
            final String currencyDisplaySymbol = rs.getString("currencyDisplaySymbol");
            final Integer currencyDigits = JdbcSupport.getInteger(rs, "currencyDigits");
            final Integer inMultiplesOf = JdbcSupport.getInteger(rs, "inMultiplesOf");
            // currency
            final CurrencyData currency = new CurrencyData(currencyCode, currencyName, currencyDigits, inMultiplesOf, currencyDisplaySymbol,
                    currencyNameCode);

            return InterestRateChartSlabData.instance(id, description, periodType, fromPeriod, toPeriod, amountRangeFrom, amountRangeTo,
                    annualInterestRate, currency);
        }

    }

    private static final class InterestRateChartSlabExtractor implements ResultSetExtractor<Collection<InterestRateChartSlabData>> {

        InterestRateChartSlabsMapper chartSlabsMapper = new InterestRateChartSlabsMapper();
        InterestIncentiveMapper incentiveMapper = new InterestIncentiveMapper();

        private final String schemaSql;

        public String schema() {
            return this.schemaSql;
        }

        private InterestRateChartSlabExtractor() {
            this.schemaSql = chartSlabsMapper.schema();
        }

        @Override
        public Collection<InterestRateChartSlabData> extractData(ResultSet rs) throws SQLException, DataAccessException {

            List<InterestRateChartSlabData> chartDataList = new ArrayList<>();

            InterestRateChartSlabData chartSlabData = null;
            Long interestRateChartSlabId = null;
            int ircIndex = 0;// Interest rate chart index
            int ircdIndex = 0;// Interest rate chart Slabs index

            while (rs.next()) {
                Long tempIrcdId = rs.getLong("ircdId");
                if (chartSlabData == null || (interestRateChartSlabId != null && !interestRateChartSlabId.equals(tempIrcdId))) {
                    interestRateChartSlabId = tempIrcdId;
                    chartSlabData = chartSlabsMapper.mapRow(rs, ircIndex++);
                    chartDataList.add(chartSlabData);
                }
                final InterestIncentiveData incentiveData = incentiveMapper.mapRow(rs, ircdIndex++);
                if (incentiveData != null) {
                    chartSlabData.addIncentives(incentiveData);
                }
            }
            return chartDataList;
        }
    }

    private static final class InterestIncentiveMapper implements RowMapper<InterestIncentiveData> {

        @Override
        public InterestIncentiveData mapRow(ResultSet rs, @SuppressWarnings("unused") int rowNum) throws SQLException {
            final Long id = JdbcSupport.getLongDefaultToNullIfZero(rs, "iriId");
            // If there are not Incentive are associated then in
            // InterestRateChartExtractor the incentive id will be null.
            if (id == null) { return null; }

            final String attributeValue = rs.getString("attributeValue");
            String attributeValueDesc = null;
            final Integer entityType = JdbcSupport.getInteger(rs, "entityType");
            final EnumOptionData entityTypeData = InterestIncentivesEnumerations.entityType(entityType);

            final Integer attributeName = JdbcSupport.getInteger(rs, "attributeName");
            if (InterestIncentiveAttributeName.isCodeValueAttribute(InterestIncentiveAttributeName.fromInt(attributeName))) {
                attributeValueDesc = rs.getString("attributeValueDesc");
            }
            final EnumOptionData attributeNameData = InterestIncentivesEnumerations.attributeName(attributeName);
            final Integer conditionType = JdbcSupport.getInteger(rs, "conditionType");
            final EnumOptionData conditionTypeData = CommonEnumerations.conditionType(conditionType, "incentive");
            final Integer incentiveType = JdbcSupport.getInteger(rs, "incentiveType");
            final EnumOptionData incentiveTypeData = InterestIncentivesEnumerations.incentiveType(incentiveType);
            final BigDecimal amount = rs.getBigDecimal("amount");

            return InterestIncentiveData.instance(id, entityTypeData, attributeNameData, conditionTypeData, attributeValue,
                    attributeValueDesc, incentiveTypeData, amount);

        }

    }

}

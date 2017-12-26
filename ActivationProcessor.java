package nl.yestelecom.middleware.processor;

import static org.apache.commons.lang.StringUtils.isEmpty;
import static org.apache.commons.lang.StringUtils.isNotEmpty;

import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import javax.transaction.Transactional;
import javax.transaction.Transactional.TxType;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import nl.yestelecom.middleware.callback.ConnectionActivationMWPostProcessor;
import nl.yestelecom.middleware.callback.InvoiceChangeMWPostProcessor;
import nl.yestelecom.middleware.dao.zygo.ZygoDbDao;
import nl.yestelecom.middleware.endpoint.ConnectionActivationService;
import nl.yestelecom.middleware.endpoint.CustomerActivationService;
import nl.yestelecom.middleware.endpoint.InvoiceChangeService;
import nl.yestelecom.middleware.exception.MWException;
import nl.yestelecom.middleware.model.ConnectionDTO;
import nl.yestelecom.middleware.model.CustomerDTO;
import nl.yestelecom.middleware.model.InvoiceDTO;
import nl.yestelecom.middleware.model.WorkOrderDTO;
import nl.yestelecom.middleware.repo.ActivationType;
import nl.yestelecom.middleware.repo.MWConnectionRepository;
import nl.yestelecom.middleware.repo.MWCustomerRepository;
import nl.yestelecom.middleware.repo.MWDealerRepository;
import nl.yestelecom.middleware.repo.MWInvoiceRepository;
import nl.yestelecom.middleware.util.Constants;
import nl.yestelecom.middleware.util.LoginUtil;
import nl.yestelecom.middleware.util.MwErrorUtil;
import nl.yestelecom.middleware.util.WorkOrderItemService;
import nl.yestelecom.middleware.util.ZygoUtils;
import nl.yestelecom.phoenix.common.utils.DateUtils;
import nl.yestelecom.phoenix.connection.model.Country.CountryCode;
import nl.yestelecom.phoenix.customer.model.Customer;
import nl.yestelecom.phoenix.deal.model.Dealer;
import nl.yestelecom.phoenix.invoice.model.Invoice;
import nl.yestelecom.phoenix.middleware.model.MWErrorType.MWErrorTypeCode;
import nl.yestelecom.phoenix.person.model.Gender;
import nl.yestelecom.phoenix.person.model.Person;
import nl.yestelecom.phoenix.sim.model.GsmSim;
import nl.yestelecom.phoenix.sim.model.Sim;

@Service
public class ActivationProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(ActivationProcessor.class);

    private static final String LEDGER_ADDRESS_CODE_PLACEHOLDER_FOR_FOREIGN_ADDRESS = "000000";

    @Autowired
    private MWCustomerRepository customerRepo;
    @Autowired
    private MWInvoiceRepository invoiceRepo;
    @Autowired
    private MWConnectionRepository connectionRepo;
    @Autowired
    private MWDealerRepository dealerRepo;
    @Autowired
    private ZygoDbDao zygoDbDao;
    @Autowired
    private CustomerActivationService customerActivation;
    @Autowired
    private InvoiceChangeService invoiceActivation;
    @Autowired
    private ConnectionActivationService connectionActivation;
    @Autowired
    private LoginUtil loginUtil;
    @Autowired
    private MwErrorUtil errorUtil;
    @Autowired
    private ConnectionActivationMWPostProcessor postActivationProcessor;
    @Autowired
    private InvoiceChangeMWPostProcessor invoiceChangePostProcessor;
    @Autowired
    private WorkOrderItemService workOrderItemService;

    @Transactional(value = TxType.REQUIRES_NEW)
    public Boolean process(Customer customerObj, ActivationType activationType) {
        loginUtil.login();
        Boolean isSuccess = Boolean.TRUE;
        Customer customer = customerRepo.findOne(customerObj.getId());
        if (null != customer) {
            try {
                CustomerDTO customerDTO = prepareCustomerData(customer, activationType);
                if (StringUtils.isBlank(customerDTO.getZygoNumber())) {
                    String zygoCustomerNo = customerActivation.createNewCustomer(customerDTO);
                    postActivationProcessor.updateCustomerNo(customerDTO.getCustId(), zygoCustomerNo);
                    LOG.info("Created new customer {} in zygo with zygo CustomerNo {}", customerDTO.getCustId(), zygoCustomerNo);
                }
            } catch (Exception e) {
                isSuccess = Boolean.FALSE;
                handleZygoError(customer, e);
            }
        }
        return isSuccess;
    }

    private void handleZygoError(Customer customer, Exception e) {
        LOG.error("Error while processing customer {} - {}", customer.getId(), e.getMessage(), e);

        //@formatter:off
        WorkOrderDTO workOrderDTO = WorkOrderDTO.builder()
                .referenceId(customer.getId())
                .errorType(MWErrorTypeCode.CUSTOMER_ACTIVATION)
                .exception(e)
                .customer(customer)
                .build();
        //@formatter:on
        errorUtil.createMwError(workOrderDTO);
    }

    @Transactional(value = TxType.REQUIRES_NEW)
    public Boolean processInvoice(Invoice inv) {
        Boolean isSuccess = Boolean.TRUE;
        final Invoice newInvoice = invoiceRepo.findOne(inv.getId());
        if (null != newInvoice) {
            try {
                InvoiceDTO invDTO = prepareInvoiceData(newInvoice);

                createLedgerAccountCode(invDTO);
                createLedgerAddressCode(invDTO);
                createLedgerPaymentDetailCode(invDTO);
                createContractCode(invDTO);
            } catch (Exception e) {
                isSuccess = Boolean.FALSE;
                handleZygoError(newInvoice, e);
            }
        }
        return isSuccess;
    }

    private void createContractCode(InvoiceDTO invDTO) throws Exception {
        if (isEmpty(invDTO.getContractCode()) && isNotEmpty(invDTO.getCustomerZygoNumber())) {
            String contractCode = invoiceActivation.createContract(invDTO, getLatestActivation(invDTO.getInvoiceId()));
            invDTO.setContractCode(contractCode);
            invoiceChangePostProcessor.updateContractCode(invDTO.getInvoiceId(), contractCode);
        }
    }

    private void createLedgerPaymentDetailCode(InvoiceDTO invDTO) throws Exception {
        if (isEmpty(invDTO.getLedgerPaymentDetailCode()) && isNotEmpty(invDTO.getLedgerAccountCode())) {
            String ledgerPaymentDetailCode = zygoDbDao.getLedgerPaymentDetailCode(invDTO.getLedgerAccountCode());

            if (isEmpty(ledgerPaymentDetailCode)) {
                if ("AI".equalsIgnoreCase(invDTO.getPayTypeCode())) {
                    ledgerPaymentDetailCode = invoiceActivation.createIbanPaymentDetail(invDTO, getLatestActivation(invDTO.getInvoiceId()));
                } else {
                    ledgerPaymentDetailCode = invoiceActivation.createOrdinaryPaymentDetail(invDTO, getLatestActivation(invDTO.getInvoiceId()));
                }
                invDTO.setLedgerPaymentDetailCode(ledgerPaymentDetailCode);
                invoiceChangePostProcessor.updateLedgerPaymentDetailCode(invDTO.getInvoiceId(), ledgerPaymentDetailCode);
            }
        }
    }

    private void createLedgerAddressCode(InvoiceDTO invDTO) throws Exception {
        if (isEmpty(invDTO.getLedgerAddressCode()) && isNotEmpty(invDTO.getLedgerAccountCode())) {
            String ledgerAddressCode;
            if (isForeignAddress(invDTO)) {
                workOrderItemService.createWorkOrderItemForForeignAddress(invDTO);
                ledgerAddressCode = LEDGER_ADDRESS_CODE_PLACEHOLDER_FOR_FOREIGN_ADDRESS;
                LOG.info("Created worklist item for foreign invoice address for invoice id {} ", invDTO.getInvoiceId());
            } else {
                // check if ledger address exists in zygo db
                ledgerAddressCode = zygoDbDao.getLedgerAddressCode(invDTO.getLedgerAccountCode());
                if (isEmpty(ledgerAddressCode)) {
                    ledgerAddressCode = invoiceActivation.createLedgerAddress(invDTO);
                }
            }
            invDTO.setLedgerAddressCode(ledgerAddressCode);
            invoiceChangePostProcessor.updateLedgerAddressCode(invDTO.getInvoiceId(), ledgerAddressCode);
        }
    }

    private void createLedgerAccountCode(InvoiceDTO invDTO) throws Exception {
        if (isEmpty(invDTO.getLedgerAccountCode()) && isNotEmpty(invDTO.getCustomerZygoNumber())) {
            String ledgerAccountCode = invoiceActivation.createLedgerAccount(invDTO);
            invDTO.setLedgerAccountCode(ledgerAccountCode);
            invoiceChangePostProcessor.updateLedgerAccountCode(invDTO.getInvoiceId(), ledgerAccountCode);
        }
    }

    private void handleZygoError(Invoice inv, Exception e) {
        LOG.error("Error while processing invoice {} - {}", inv.getId(), e.getMessage(), e);

        //@formatter:off
        WorkOrderDTO workOrderDTO = WorkOrderDTO.builder()
                .referenceId(inv.getId())
                .errorType(MWErrorTypeCode.INVOICE_ACTIVATION)
                .exception(e)
                .customer(inv.getCustomer())
                .build();
        //@formatter:on
        errorUtil.createMwError(workOrderDTO);
    }

    @Transactional(value = TxType.REQUIRES_NEW)
    public void processConnection(GsmSim gsmSim, ActivationType activationType) {
        GsmSim con = connectionRepo.findOne(gsmSim.getId());
        if (null != con) {
            try {
                ConnectionDTO connectionDTO = prepareConnectionData(con, activationType);
                String serviceCode = addNewConnectionInZygo(con, connectionDTO);
                doPostProcessing(activationType, con, serviceCode);
            } catch (Exception e) {
                handleZygoError(con, e);
            }
        }
    }

    private void doPostProcessing(ActivationType activationType, GsmSim con, String serviceCode) {
        if (isNotEmpty(serviceCode)) {
            if (activationType == ActivationType.INSURANCE_PRODUCT) {
                postActivationProcessor.connectionCreatedForInsuranceProduct(con.getId(), serviceCode);
            } else {
                postActivationProcessor.connectionCreatedInZygo(con.getId(), serviceCode);
            }
        }
    }

    private String addNewConnectionInZygo(GsmSim con, ConnectionDTO connectionDTO) throws MWException {
        String serviceCode = con.getServiceCode();
        if (StringUtils.isEmpty(serviceCode)) {
            serviceCode = zygoDbDao.getServiceCode(connectionDTO.getGsmNumberForZygo(), connectionDTO.getSimNumberForZygo());
            if (StringUtils.isEmpty(serviceCode)) {
                boolean isMobileNumberInUse = zygoDbDao.isMobileNumberInUse(connectionDTO.getGsmNumberForZygo());
                if (isMobileNumberInUse) {
                    String error = String.format("Gsm Number %s is already active in Zygo.", con.getGsmNumber());
                    throw new MWException(error);
                }

                boolean isSimNumberInUse = zygoDbDao.isSimNumberInUse(connectionDTO.getSimNumberForZygo());
                if (isSimNumberInUse) {
                    String error = String.format("Sim Number %s is already active in Zygo.", connectionDTO.getSimNumberForZygo());
                    throw new MWException(error);
                }

                serviceCode = connectionActivation.addNewConnectionInZygo(connectionDTO);
                LOG.info("Created new connection {} in zygo with service code {}", connectionDTO.getGsmNumberForZygo(), serviceCode);
            }
        }
        return serviceCode;
    }

    private void handleZygoError(GsmSim con, Exception e) {
        LOG.error("Error while activating gsm sim id {} - {}", con.getId(), e.getMessage(), e);

        //@formatter:off
        WorkOrderDTO workOrderDTO = WorkOrderDTO.builder()
                .referenceId(con.getId())
                .errorType(MWErrorTypeCode.CONNECTION_ACTIVATION)
                .exception(e)
                .gsmSim(con)
                .build();
        //@formatter:on
        errorUtil.createMwError(workOrderDTO);
    }

    private CustomerDTO prepareCustomerData(Customer customer, ActivationType activationType) {
        Dealer dealer;
        if (activationType == ActivationType.ORDINARY) {
            dealer = dealerRepo.findDealerForOrdinaryConnections(customer.getId()).get(0);
        } else if (activationType == ActivationType.INSURANCE_PRODUCT) {
            dealer = dealerRepo.findDealerForInsuranceConnections(customer.getId()).get(0);
        } else {
            dealer = dealerRepo.findDealerForCOVConnections(customer.getId()).get(0);
        }

        //@formatter:off
        CustomerDTO customerDTO = CustomerDTO.builder()
            .isPrivate(Boolean.TRUE.equals(customer.getIsPrivate()))
            .custId(customer.getId())
            .zygoNumber(customer.getZygoNumber() == null ? null : String.valueOf(customer.getZygoNumber()))
            .dealerCode(dealer.getCode())
            .build();
        //@formatter:on

        if (customerDTO.isPrivate()) {
            Person person = customer.getPerson();
            customerDTO.setFirstName(person.getInitials());
            customerDTO.setMiddleName(person.getInsertion());
            customerDTO.setLastName(person.getLastname());
            customerDTO.setDob(DateUtils.toLocalDateTime(person.getDateOfBirth()));
            customerDTO.setTitle(person.getGender() == Gender.M ? "Dhr" : "Mw");
        } else {
            customerDTO.setCompanyName(customer.getCompany().getCompanyName());
        }
        return customerDTO;
    }

    private InvoiceDTO prepareInvoiceData(Invoice inv) {
        String zipCode = ZygoUtils.formatZipCodeforZygo(inv.getAddressData().getZipcode());
        String zipCodePB = ZygoUtils.formatZipCodeforZygo(inv.getAddressData().getZipcodePb());
        String cityCode = zygoDbDao.getCityCode(inv.getAddressData().getCity(), zipCode);
        String cityCodePB = zygoDbDao.getCityCode(inv.getAddressData().getCityPb(), zipCodePB);

        //@formatter:off
        return InvoiceDTO.builder()
                .invoiceId(inv.getId())
                .payId(inv.getPaymentTypes().getId())
                .ledgerAccountCode(inv.getLedgerAccountCode())
                .ledgerAddressCode(inv.getLedgerAddressCode())
                .ledgerPaymentDetailCode(inv.getLedgerPaymentdetailCode())
                .contractCode(inv.getContractCode())
                .payTypeCode(inv.getPaymentTypes().getCode())
                .invoiceTypeCode(inv.getInvoiceTypes().getCode())
                .customerName(inv.getCustomerName())
                .customerZygoNumber(String.valueOf(inv.getCustomer().getZygoNumber()))
                .contactName(inv.getOwnerAccount())
                .telephone(inv.getAddressData().getTelephone())
                .postBox(inv.getAddressData().getPostbox())
                .street(inv.getAddressData().getStreet())
                .houseNumber(inv.getAddressData().getHousenumber())
                .flatNumber(inv.getAddressData().getAddition())
                .email(inv.getAddressData().getEmail())
                .usePostBox(Boolean.TRUE.equals(inv.getIsUsePostbox()))
                .iban(inv.getIban())
                .mandateId(inv.getMandateId())
                .accountName(inv.getOwnerAccount())
                .zipCode(zipCode)
                .zipcodePB(zipCodePB)
                .cityCode(cityCode)
                .cityCodePB(cityCodePB)
                .countryCode(inv.getAddressData().getCountry() == null ? null :inv.getAddressData().getCountry().getCode())
                .countryCodePB(inv.getAddressData().getCountryPb() == null ? null :inv.getAddressData().getCountryPb().getCode())
                .build();
        //@formatter:on
    }

    private ConnectionDTO prepareConnectionData(GsmSim con, ActivationType activationType) {
        //@formatter:off
        Sim simOnActivation = con.getSim();
        //This piece of code was copied from the old middleware. But it does not look right. Hence commenting it out.
//        try{
//            GsmSim hisGsmSim = (GsmSim)AuditReaderFactory.get(entityManager).createQuery()
//                   .forRevisionsOfEntity(GsmSim.class, true, false)
//                   .add(AuditEntity.property("id").eq(con.getId()))
//                   .add(AuditEntity.property("activationDate").isNotNull())
//                   .addOrder(AuditEntity.revisionNumber().asc())
//                   .setMaxResults(1)
//                   .getSingleResult();
//            simOnActivation = hisGsmSim.getSim();
//        } catch(Exception e) {
//            simOnActivation = con.getSim();
//        }

        return ConnectionDTO.builder()
               .gssId(con.getId())
               .simOnActivation(simOnActivation.getSimNo())
               .serviceCode(con.getServiceCode())
               .username(con.getUserName())
               .gsmNumber(con.getGsmNumber().getGsmNo())
               .dealerCode(con.getSubdeal().getDeal().getDealer().getCode())
               .activationDate(DateUtils.getStartOfDate(ActivationType.INSURANCE_PRODUCT == activationType ? new Date(): con.getActivationDate()))
               .tariffPlanCode(con.getTariffPlan().getCode())
               .isPorting(Boolean.TRUE.equals(con.getIsPorting()))
               .dataSubscriptionCode(con.getDataSubscriptionPlan() == null ? null :con.getDataSubscriptionPlan().getCode())
               .isM2M("M2M".equalsIgnoreCase(simOnActivation.getType2()))
               .longSim(simOnActivation.getLongSimNr())
               .customerZygoNumber(String.valueOf(con.getSubdeal().getDeal().getContract().getCustomer().getZygoNumber()))
               .invoiceContractCode(con.getInvoice().getContractCode())
               .build();
        //@formatter:on
    }

    private boolean isForeignAddress(InvoiceDTO inv) {
        String countryCode = Boolean.TRUE.equals(inv.isUsePostBox()) ? inv.getCountryCodePB() : inv.getCountryCode();
        return countryCode != null && !(CountryCode.NL == CountryCode.valueOf(countryCode));
    }

    private String getLatestActivation(Long invoiceId) {
        List<GsmSim> connections = connectionRepo.findConnectionsForActivation(invoiceId);
        Optional<GsmSim> gssMinAtivationDate = connections.stream().filter(c -> c.getActivationDate() != null).min(Comparator.comparing(GsmSim::getActivationDate));

        Date activationDate;
        if (gssMinAtivationDate.isPresent()) {
            activationDate = gssMinAtivationDate.get().getActivationDate();
        } else {
            activationDate = new Date();
        }
        return DateUtils.format(DateUtils.toLocalDateTime(activationDate).truncatedTo(ChronoUnit.DAYS), Constants.ZYGO_DATE);
    }
}

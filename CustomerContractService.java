package nl.yestelecom.phoenix.customer.contract.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import nl.yestelecom.phoenix.customer.contract.DTO.CustomerContractDTO;
import nl.yestelecom.phoenix.customer.contract.model.CustomerContractRepository;
import nl.yestelecom.phoenix.deal.model.Deal;
import nl.yestelecom.phoenix.deal.model.DealDetails;
import nl.yestelecom.phoenix.deal.model.Subdeal;
import nl.yestelecom.phoenix.deal.repo.DealDetailsRepository;
import nl.yestelecom.phoenix.deal.repo.DealRepository;
import nl.yestelecom.phoenix.subdeal.repository.DatasubSubdealRepository;
import nl.yestelecom.phoenix.subdeal.repository.SubdealRepository;

@Service
@Transactional
public class CustomerContractService {

    @Autowired
    private CustomerContractRepository cusConRepo;
    @Autowired
    private DealDetailsRepository dealDetailsRepository;
    @Autowired
    private DealRepository dealRepository;
    @Autowired
    private nl.yestelecom.phoenix.subdeal.repository.TariffsubSubdealRepository tariffsubSubdealRepository;
    @Autowired
    private DatasubSubdealRepository datasubSubdealRepository;
    @Autowired
    private SubdealRepository subdealRepository;

    public List<CustomerContractDTO> getCustomerContractForEmployees(Long cusId) {
        List<CustomerContractDTO> customerContractDTOs = new ArrayList<>();

        customerContractDTOs = getCustomerContracts(cusId, null);
		
        return customerContractDTOs;
    }

    public List<CustomerContractDTO> getCustomerContractForDealer(Long cusId, Long dlrId) {

        List<CustomerContractDTO> customerContractDTOs = new ArrayList<>();
        customerContractDTOs = getCustomerContracts(cusId, dlrId);

        return customerContractDTOs;
    }

    private List<CustomerContractDTO> getCustomerContracts(Long cusId, Long dlrId) {
        final List<DealDetails> dealDetailsList;
        if (null == dlrId) {
            dealDetailsList = dealDetailsRepository.findByCustomerId(cusId);
        } else {
            dealDetailsList = dealDetailsRepository.findByCustomerIdAndDealerId(cusId, dlrId);
        }
        final List<String> dealStatusForFilter = Arrays.asList("Aanvraag geannuleerd");
        List<CustomerContractDTO> customerContractDTOs = new ArrayList<>();

        customerContractDTOs = populateCustomerContracts(dealDetailsList, customerContractDTOs);

        final Predicate<CustomerContractDTO> dealStatusPredicate = d -> dealStatusForFilter.contains(d.getDealStatus());
        customerContractDTOs = customerContractDTOs.stream().filter(dealStatusPredicate.negate()).collect(Collectors.toList());

        return customerContractDTOs;
    }

    private List<CustomerContractDTO> populateCustomerContracts(final List<DealDetails> dealDetailsList, final List<CustomerContractDTO> customerContractDTOs) {

        for (final DealDetails dealDetails : dealDetailsList) {

            final Deal deal = dealRepository.findOne(dealDetails.getDealId());
            final List<Subdeal> subdeals = subdealRepository.findByDealId(dealDetails.getDealId());
            if (!subdeals.isEmpty()) {
                final Set<Long> subIds = subdeals.stream().map(Subdeal::getId).collect(Collectors.toSet());
                final Set<Integer> subDealContractLengths = subdeals.stream().filter(s -> null != s.getContractLength()).map(Subdeal::getContractLength).collect(Collectors.toSet());
                final Integer maxContractLength = tariffsubSubdealRepository.findMaxContractLengthBySubdealIdIn(subIds);
                final Integer maxContractLengthData = datasubSubdealRepository.findMaxContractLengthBySubdealIdIn(subIds);

                final CustomerContractDTO customerContractDTO = convertToDTO(dealDetails);
                customerContractDTO.setContractLength(null != maxContractLength ? maxContractLength : subDealContractLengths.isEmpty() ? 0 : Collections.max(subDealContractLengths));
                customerContractDTO.setContractLengthData(null != maxContractLengthData ? maxContractLengthData : subDealContractLengths.isEmpty() ? 0 : Collections.max(subDealContractLengths));
                customerContractDTO.setIsSpecial(Boolean.TRUE.equals(deal.getIsSpecial()));
                customerContractDTO.setDealStatusDate(null != deal.getStatusDate() ? deal.getStatusDate() : null);
                customerContractDTOs.add(customerContractDTO);

            }

        }

        return customerContractDTOs;

    }

    private CustomerContractDTO convertToDTO(DealDetails dealDetails) {
        //@formatter:off
        return  CustomerContractDTO.builder().
              dealId(dealDetails.getDealId())
              .dealerId(dealDetails.getDealerId())
              .createdByUsertype(dealDetails.getCreatedByUserType().toString())
              .customerId(dealDetails.getCustomerId())
              .dealStatus(dealDetails.getDealStatusDescription())
              .dealStatusCode(dealDetails.getDealStatusCode())
              .offerNumber(dealDetails.getDealNumber())
              .type(dealDetails.getIsRetention()? "Retentie": "Acquisitie")
              .totalSim(null !=dealDetails.getNumberOfSims()?dealDetails.getNumberOfSims():0)
              .build();
          //@formatter:on
    }

}

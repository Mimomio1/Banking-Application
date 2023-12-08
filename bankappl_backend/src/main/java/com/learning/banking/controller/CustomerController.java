package com.learning.banking.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.learning.banking.entity.Account;
import com.learning.banking.entity.Beneficiary;
import com.learning.banking.entity.Customer;
import com.learning.banking.entity.Role;
import com.learning.banking.entity.Transaction;
import com.learning.banking.enums.AccountStatus;
import com.learning.banking.enums.BeneficiaryStatus;
import com.learning.banking.enums.CustomerStatus;
import com.learning.banking.enums.TransactionType;
import com.learning.banking.enums.UserRoles;
import com.learning.banking.exceptions.IdNotFoundException;
import com.learning.banking.exceptions.InsufficientFundsException;
import com.learning.banking.exceptions.NoDataFoundException;
import com.learning.banking.exceptions.NoRecordsFoundException;
import com.learning.banking.exceptions.TransferException;
import com.learning.banking.exceptions.UserNameAlreadyExistsException;
import com.learning.banking.payload.request.AddBeneficiaryRequest;
import com.learning.banking.payload.request.ApproveAccountRequest;
import com.learning.banking.payload.request.CreateAccountRequest;
import com.learning.banking.payload.request.CreateUserRequest;
import com.learning.banking.payload.request.ResetPasswordRequest;
import com.learning.banking.payload.request.SignInRequest;
import com.learning.banking.payload.request.TransferRequest;
import com.learning.banking.payload.request.UpdateCustomerRequest;
import com.learning.banking.payload.response.AccountDetailsResponse;
import com.learning.banking.payload.response.AddBeneficiaryResponse;
import com.learning.banking.payload.response.AllAccountsResponse;
import com.learning.banking.payload.response.ApiMessage;
import com.learning.banking.payload.response.BeneficiaryResponse;
import com.learning.banking.payload.response.CreateAccountResponse;
import com.learning.banking.payload.response.CustomerResponse;
import com.learning.banking.payload.response.GetCustomerQandAResponse;
import com.learning.banking.payload.response.JwtResponse;
import com.learning.banking.payload.response.RegisterCustomerResponse;
import com.learning.banking.payload.response.StaffApproveAccountResponse;
import com.learning.banking.payload.response.TransferResponse;
import com.learning.banking.security.jwt.JwtUtils;
import com.learning.banking.security.service.UserDetailsImpl;
import com.learning.banking.service.AccountService;
import com.learning.banking.service.CustomerService;
import com.learning.banking.service.RoleService;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/customer")
@Validated
public class CustomerController {
	@Autowired
	private CustomerService customerService;
	@Autowired
	private AccountService accountService;
	@Autowired
	private RoleService roleService;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private AuthenticationManager authenticationManager;

	@Autowired
	private JwtUtils jwtUtils;
	
	private Authentication getAuthentication() {
		return SecurityContextHolder.getContext().getAuthentication();
	}

	@PostMapping("/register")
	public ResponseEntity<?> registerCustomer(@Valid @RequestBody CreateUserRequest registerUserRequest) {
		if (!customerService.existsByUsername(registerUserRequest.getUsername())) {
			Customer customer = new Customer();
			customer.setFullname(registerUserRequest.getFullname());
			customer.setUsername(registerUserRequest.getUsername());
			String password = passwordEncoder.encode(registerUserRequest.getPassword());
			customer.setPassword(password);
	
			customer.setAadhar(registerUserRequest.getAadhar());
			customer.setDateCreated(LocalDateTime.now());
			customer.setFullname(registerUserRequest.getFullname());
			customer.setPan(registerUserRequest.getPan());
			customer.setPhone(registerUserRequest.getPhone());
			customer.setSecretQuestion(registerUserRequest.getSecretQuestion());
			customer.setSecretAnswer(registerUserRequest.getSecretAnswer());
			customer.setStatus(CustomerStatus.ENABLED);
	
			Role userRole = roleService.findByRoleName(UserRoles.ROLE_CUSTOMER)
					.orElseThrow(() -> new IdNotFoundException("role id not found exception"));
			customer.getRoles().add(userRole);
		
			Customer c = customerService.addCustomer(customer);

			RegisterCustomerResponse cr = new RegisterCustomerResponse();
			cr.setId(c.getCustomerID());
			cr.setUsername(c.getUsername());
			cr.setFullname(c.getFullname());
			return ResponseEntity.status(201).body(cr);
		} else {
			throw new UserNameAlreadyExistsException("Username: " + registerUserRequest.getUsername() + " already exists!");
		}
	}

	@PostMapping("/authenticate")
	public ResponseEntity<?> signInUser(@Valid @RequestBody SignInRequest signInRequest) {
		Authentication authentication = authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(signInRequest.getUsername(), signInRequest.getPassword()));
		SecurityContextHolder.getContext().setAuthentication(authentication);
		String jwt = jwtUtils.generateToken(authentication);

		UserDetailsImpl userDetailsImpl = (UserDetailsImpl) authentication.getPrincipal();
		List<String> roles = userDetailsImpl.getAuthorities().stream().map(e -> e.getAuthority())
				.collect(Collectors.toList());

		return ResponseEntity.ok(new JwtResponse(jwt, userDetailsImpl.getId(), userDetailsImpl.getUsername(), roles));
	}

	@PreAuthorize("hasRole('CUSTOMER')")
	@PostMapping("/{customerId}/account")
	public ResponseEntity<?> registerAccount(@PathVariable Long customerId,
			@Valid @RequestBody CreateAccountRequest request) throws NoRecordsFoundException {
		Account account = new Account();
		account.setCustomer(customerService.getCustomerByID(customerId)
				.orElseThrow(() -> new NoRecordsFoundException("Customer with ID: " + customerId + " not found")));
		account.setAccountBalance(request.getAccountBalance());
		account.setAccountStatus(AccountStatus.DISABLED);
		account.setAccountType(request.getAccountType());
		account.setApproved(false);
		account.setDateOfCreation(LocalDateTime.now());

		Account newAccount = accountService.addAccount(account);
		CreateAccountResponse response = new CreateAccountResponse(newAccount);

		return ResponseEntity.status(200).body(response);
	}

	@PutMapping("/{customerID}/account/{accountNumber}")
	@PreAuthorize("hasRole('STAFF')")
	public ResponseEntity<?> approveTheCustomerAccount(@PathVariable Long customerID, @PathVariable Long accountNumber,
			@RequestBody ApproveAccountRequest request) throws NoRecordsFoundException {
		Long accountNum = request.getAccountNumber();
		if (accountService.existsByAccountNumber(accountNum)) {
			Authentication authentication = getAuthentication();
			UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
			final Customer approver = customerService.getCustomerByID(userDetails.getId()).orElseThrow(() ->{
				return new NoRecordsFoundException("Customer with ID: " + userDetails.getId() + " not found");
			});

			Account account = accountService.findAccountByAccountNumber(accountNum).get();
			if (request.getApproved().equalsIgnoreCase("yes")) {
				account.setApproved(true);
				account.setAccountStatus(AccountStatus.ENABLED);
			} else {
				account.setApproved(false);
				account.setAccountStatus(AccountStatus.DISABLED);
			}
			account.setApprovedBy(approver);
			
			account = accountService.updateAccount(account);

			StaffApproveAccountResponse accountResponse = new StaffApproveAccountResponse();
			accountResponse.setAccountNumber(account.getAccountNumber());
			if (account.isApproved() == true) {
				accountResponse.setApproved("yes");
			} else {
				accountResponse.setApproved("no");
			}
			return ResponseEntity.ok(accountResponse);
		} else {
			throw new NoDataFoundException("Please check Account Number");
		}
	}

	@PreAuthorize("hasRole('CUSTOMER')")
	@GetMapping("/{customerID}/account")
	public ResponseEntity<?> getCustomerAccounts(@PathVariable("customerID") Long id) throws NoRecordsFoundException {
		System.out.println("start");
		if (customerService.existsByID(id)) {
			List<Account> accounts = accountService.findAccountsByCustomerCustomerID(id);
			List<AllAccountsResponse> accountsResponses = new ArrayList<>();
			accounts.forEach(a -> {
				AllAccountsResponse account = new AllAccountsResponse();
				account.setAccountNumber(a.getAccountNumber());
				account.setAccountBalance(a.getAccountBalance());
				account.setAccountStatus(a.getAccountStatus());
				account.setAccountType(a.getAccountType());
				accountsResponses.add(account);
			});

			return ResponseEntity.ok(accountsResponses);
		} else {
			throw new NoDataFoundException("No customer data found");
		}
	}

	@PreAuthorize("hasRole('CUSTOMER')")
	@GetMapping("/{customerID}")
	public ResponseEntity<?> getCustomerByID(@PathVariable Long customerID) throws NoRecordsFoundException {

		Customer customer = customerService.getCustomerByID(customerID).orElseThrow(() -> {
			return new NoRecordsFoundException("Customer with ID: " + customerID + " not found");
		});

		return ResponseEntity.ok(new CustomerResponse(customer));
	}

	@PreAuthorize("hasRole('CUSTOMER')")
	@PutMapping("/{customerID}")
	public ResponseEntity<?> updateCustomerByID(@PathVariable Long customerID,
			@Valid @RequestBody UpdateCustomerRequest request) throws NoRecordsFoundException {

		Customer customer = customerService.getCustomerByID(customerID).orElseThrow(() -> {
			return new NoRecordsFoundException("Customer with ID: " + customerID + " not found");
		});

		customer.setFullname(request.getFullname());
		customer.setPhone(request.getPhone());
		customer.setPan(request.getPan());
		customer.setAadhar(request.getAadhar());
		customer.setSecretQuestion(request.getSecretQuestion());
		customer.setSecretAnswer(request.getSecretAnswer());

		Customer updated = customerService.updateCustomer(customer);

		return ResponseEntity.ok(new CustomerResponse(updated));
	}

	@PreAuthorize("hasRole('CUSTOMER')")
	@GetMapping("/{customerID}/account/{accountID}")
	public ResponseEntity<?> getCustomerAccountByID(@PathVariable Long customerID, @PathVariable Long accountID)
			throws NoRecordsFoundException {

		Customer customer = customerService.getCustomerByID(customerID).orElseThrow(() -> {
			return new NoRecordsFoundException("Customer with ID: " + customerID + " not found");
		});

		Account account = customer.getAccounts().stream().filter(acc -> {
			return acc.getAccountNumber() == accountID;
		}).findFirst().orElseThrow(() -> {
			return new NoRecordsFoundException("Account with ID: " + accountID + " not found");
		});

		return ResponseEntity.status(HttpStatus.OK).body(new AccountDetailsResponse(account));
	}

	@PreAuthorize("hasRole('CUSTOMER')")
	@PostMapping("/{customerID}/beneficiary")
	public ResponseEntity<?> addBeneficiaryToCustomer(@PathVariable Long customerID,
			@Valid @RequestBody AddBeneficiaryRequest request) throws NoRecordsFoundException {
	
		Customer customer = customerService.getCustomerByID(customerID).orElseThrow(() -> {
			return new NoRecordsFoundException("Customer with ID: " + customerID + " not found");
		});

		Account beneficiaryAccount = accountService.getAccountByAccountNumber(request.getAccountNumber())
				.orElseThrow(() -> {
					return new NoRecordsFoundException(
							"Beneficiary with account number: " + request.getAccountNumber() + " not found");
				});

		boolean accAlreadyAdded = customer.getBeneficiaries().stream().anyMatch(b -> {
			return b.getAccount().getAccountNumber() == request.getAccountNumber();
		});
		
		if (accAlreadyAdded) {
			throw new IllegalArgumentException("Beneficiary with account number: " + request.getAccountNumber() + " already added");
		} else {
			Beneficiary beneficiary = new Beneficiary(beneficiaryAccount, customer);
			beneficiary.setAddedDate(LocalDate.now());
			beneficiary.setApproved(false);
			beneficiary.setActive(BeneficiaryStatus.YES);

			customer.getBeneficiaries().add(beneficiary);

			Customer updatedCustomer = customerService.updateCustomer(customer);

			Beneficiary addedBeneficiary = updatedCustomer.getBeneficiaries().stream().filter(b -> {
				return b.getAccount().getAccountNumber() == request.getAccountNumber();
			}).findFirst().orElseThrow(() -> {
				return new NoRecordsFoundException(
						"Beneficiary with account number: " + request.getAccountNumber() + " not found");
			});

			return ResponseEntity.status(HttpStatus.CREATED).body(new AddBeneficiaryResponse(addedBeneficiary));
		}
	}

	@PreAuthorize("hasRole('CUSTOMER')")
	@GetMapping("/{customerID}/beneficiary")
	public ResponseEntity<?> getBeneficiariesForCustomer(@PathVariable Long customerID) throws NoRecordsFoundException {

		Customer customer = customerService.getCustomerByID(customerID).orElseThrow(() -> {
			return new NoRecordsFoundException("Customer with ID: " + customerID + " not found");
		});

		List<BeneficiaryResponse> beneficiariesReponseList = customer.getBeneficiaries().stream().map(b -> {
			return new BeneficiaryResponse(b);
		}).collect(Collectors.toList());

		return ResponseEntity.status(HttpStatus.OK).body(beneficiariesReponseList);
	}

	@PreAuthorize("hasRole('CUSTOMER')")
	@DeleteMapping("/{customerID}/beneficiary/{beneficiaryID}")
	public ResponseEntity<?> deleteBeneficiaryFromCustomer(@PathVariable Long customerID,
			@PathVariable Long beneficiaryID) throws NoRecordsFoundException {

		Customer customer = customerService.getCustomerByID(customerID).orElseThrow(() -> {
			return new NoRecordsFoundException("Customer with ID: " + customerID + " not found");
		});

		boolean removed = customer.getBeneficiaries().removeIf(b -> {
			return b.getBeneficiaryID() == beneficiaryID;
		});

		if (removed) {
			customer = customerService.updateCustomer(customer);
			return ResponseEntity.ok(new ApiMessage("Beneficiary deleted successfully"));
		} else {

			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiMessage("Unable to remove beneficiary"));
		}
	}

	@PreAuthorize("hasRole('CUSTOMER')")
	@PutMapping("/transfer")
	public ResponseEntity<?> accountTransferByCustomer(@Valid @RequestBody TransferRequest request)
			throws NoRecordsFoundException, InsufficientFundsException {

		final Account fromAccount = accountService.getAccountByAccountNumber(request.getFromAccNumber())
				.orElseThrow(() -> {
					return new NoRecordsFoundException(
							"Account number: " + request.getFromAccNumber() + " cannot be found");
				});
		final Account toAccount = accountService.getAccountByAccountNumber(request.getToAccNumber()).orElseThrow(() -> {
			return new NoRecordsFoundException("Account number: " + request.getToAccNumber() + " cannot be found");
		});

		final Customer initiatedBy = customerService.getCustomerByID(request.getBy()).orElseThrow(() -> {
			return new NoRecordsFoundException("Customer with ID: " + request.getBy() + " not found");
		});

		if (fromAccount.isApproved() && toAccount.isApproved()) {
			
			if (fromAccount.getAccountBalance().subtract(request.getAmount()).compareTo(BigDecimal.ZERO) < 0) {
			
				throw new InsufficientFundsException(
						String.format("Account number: %d does not have sufficient funds to process transfer",
								request.getFromAccNumber()));
			} else {
				
				fromAccount.setAccountBalance(fromAccount.getAccountBalance().subtract(request.getAmount()));
		
				toAccount.setAccountBalance(toAccount.getAccountBalance().add(request.getAmount()));
			}

			final LocalDateTime now = LocalDateTime.now();

			Transaction fromTransaction = new Transaction();
			fromTransaction.setDate(now);
			fromTransaction.setReference(request.getReason());
			fromTransaction.setAmount(request.getAmount().negate());
			fromTransaction.setTransactionType(TransactionType.DEBIT); 
			fromTransaction.setInitiatedBy(initiatedBy);
			fromTransaction.setAccount(fromAccount);
	
			fromAccount.getTransactions().add(fromTransaction);

			Transaction toTransaction = new Transaction();
			toTransaction.setDate(now);
			toTransaction.setReference(request.getReason());
			toTransaction.setAmount(request.getAmount());
			toTransaction.setTransactionType(TransactionType.DEBIT); 
			toTransaction.setInitiatedBy(initiatedBy);
			toTransaction.setAccount(toAccount);
			toAccount.getTransactions().add(toTransaction);

			accountService.updateAccounts(Arrays.asList(fromAccount, toAccount));

			TransferResponse response = new TransferResponse();
			response.setFromAccNumber(request.getFromAccNumber());
			response.setToAccNumber(request.getToAccNumber());
			response.setAmount(request.getAmount());
			response.setReason(request.getReason());
			response.setBy(request.getBy());

			return ResponseEntity.ok(response);
		} else {
			throw new TransferException("Both accounts must be approved to perform a transfer.");
		}
	}

	@GetMapping("/{username}/forgot/question/answer")
	public ResponseEntity<?> getCustomerSecurityQandA(@PathVariable String username) throws NoRecordsFoundException {

		Customer customer = customerService.getCustomerByUsername(username).orElseThrow(() -> {
			return new NoRecordsFoundException("Customer with username: " + username + " not found");
		});

		return ResponseEntity
				.ok(new GetCustomerQandAResponse(customer.getSecretQuestion(), customer.getSecretAnswer()));
	}

	@PutMapping("/{username}/forgot")
	public ResponseEntity<?> updateForgottenPassword(@PathVariable String username,
			@Valid @RequestBody ResetPasswordRequest request) throws NoRecordsFoundException {
		
		Customer customer = customerService.getCustomerByUsername(username).orElseThrow(() -> {
			return new NoRecordsFoundException("Customer with username: " + username + " not found");
		});

		if (!Objects.equals(request.getPassword(), request.getConfirmPassword())) {
		
			return ResponseEntity.badRequest().body(new ApiMessage("Sorry password not updated"));
		} else {
			
			customer.setPassword(passwordEncoder.encode(request.getPassword()));
			customerService.updateCustomer(customer);

			return ResponseEntity.ok(new ApiMessage("Password updated successfully"));
		}
	}
}

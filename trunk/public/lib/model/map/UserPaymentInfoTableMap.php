<?php


/**
 * This class defines the structure of the 'user_payment_info' table.
 *
 *
 * This class was autogenerated by Propel 1.4.2 on:
 *
 * Thu 26 Apr 2012 01:25:36 PM EDT
 *
 *
 * This map class is used by Propel to do runtime db structure discovery.
 * For example, the createSelectSql() method checks the type of a given column used in an
 * ORDER BY clause to know whether it needs to apply SQL to make the ORDER BY case-insensitive
 * (i.e. if it's a text column type).
 *
 * @package    lib.model.map
 */
class UserPaymentInfoTableMap extends TableMap {

	/**
	 * The (dot-path) name of this class
	 */
	const CLASS_NAME = 'lib.model.map.UserPaymentInfoTableMap';

	/**
	 * Initialize the table attributes, columns and validators
	 * Relations are not initialized by this method since they are lazy loaded
	 *
	 * @return     void
	 * @throws     PropelException
	 */
	public function initialize()
	{
	  // attributes
		$this->setName('user_payment_info');
		$this->setPhpName('UserPaymentInfo');
		$this->setClassname('UserPaymentInfo');
		$this->setPackage('lib.model');
		$this->setUseIdGenerator(true);
		// columns
		$this->addPrimaryKey('USER_PAYMENT_INFO_ID', 'UserPaymentInfoId', 'INTEGER', true, 11, null);
		$this->addColumn('FK_USER_ID', 'FkUserId', 'INTEGER', false, 11, null);
		$this->addColumn('USER_PAYMENT_INFO_FIRST_NAME', 'UserPaymentInfoFirstName', 'VARCHAR', false, 255, null);
		$this->addColumn('USER_PAYMENT_INFO_LAST_NAME', 'UserPaymentInfoLastName', 'VARCHAR', false, 255, null);
		$this->addColumn('USER_PAYMENT_INFO_CC_TYPE', 'UserPaymentInfoCcType', 'TINYINT', false, 1, null);
		$this->addColumn('USER_PAYMENT_INFO_CC_NUMBER', 'UserPaymentInfoCcNumber', 'VARCHAR', false, 16, null);
		$this->addColumn('USER_PAYMENT_INFO_SECURITY_CODE', 'UserPaymentInfoSecurityCode', 'VARCHAR', false, 4, null);
		$this->addColumn('USER_PAYMENT_INFO_EXPIRATION_MONTH', 'UserPaymentInfoExpirationMonth', 'VARCHAR', false, 2, null);
		$this->addColumn('USER_PAYMENT_INFO_EXPIRATION_YEAR', 'UserPaymentInfoExpirationYear', 'VARCHAR', false, 4, null);
		$this->addColumn('USER_PAYMENT_INFO_B_ADDRESS_1', 'UserPaymentInfoBAddress1', 'VARCHAR', false, 255, null);
		$this->addColumn('USER_PAYMENT_INFO_B_ADDRESS_2', 'UserPaymentInfoBAddress2', 'VARCHAR', false, 255, null);
		$this->addColumn('USER_PAYMENT_INFO_B_CITY', 'UserPaymentInfoBCity', 'VARCHAR', false, 255, null);
		$this->addColumn('USER_PAYMENT_INFO_B_STATE', 'UserPaymentInfoBState', 'VARCHAR', false, 255, null);
		$this->addColumn('USER_PAYMENT_INFO_B_ZIP', 'UserPaymentInfoBZip', 'VARCHAR', false, 10, null);
		$this->addColumn('USER_PAYMENT_INFO_PAYPAL_EMAIL_ADDRESS', 'UserPaymentInfoPaypalEmailAddress', 'VARCHAR', false, 255, null);
		$this->addColumn('USER_PAYMENT_INFO_CREATED_AT', 'UserPaymentInfoCreatedAt', 'TIMESTAMP', false, null, null);
		$this->addColumn('USER_PAYMENT_INFO_UPDATED_AT', 'UserPaymentInfoUpdatedAt', 'TIMESTAMP', false, null, null);
		// validators
	} // initialize()

	/**
	 * Build the RelationMap objects for this table relationships
	 */
	public function buildRelations()
	{
	} // buildRelations()

	/**
	 * 
	 * Gets the list of behaviors registered for this table
	 * 
	 * @return array Associative array (name => parameters) of behaviors
	 */
	public function getBehaviors()
	{
		return array(
			'symfony' => array('form' => 'true', 'filter' => 'true', ),
			'symfony_behaviors' => array(),
		);
	} // getBehaviors()

} // UserPaymentInfoTableMap
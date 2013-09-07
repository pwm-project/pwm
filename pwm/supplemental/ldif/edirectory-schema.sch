SchemaExtension DEFINITIONS ::=
BEGIN 

-- attributeTypes: ( 
--   1.3.6.1.4.1.35015.1.2.1 
--   NAME 'pwmEventLog' 
--   SYNTAX 1.3.6.1.4.1.1466.115.121.1.40 
--  ) 

"pwmEventLog" ATTRIBUTE ::=
 { 
	 SyntaxID 	 SYN_OCTET_STRING,
	 Flags 	{ DS_SYNC_IMMEDIATE }, 
	 ASN1ObjID 	{ 1 3 6 1 4 1 35015 1 2 1 } 
 }

-- attributeTypes: ( 
--   1.3.6.1.4.1.35015.1.2.2 
--   NAME 'pwmResponseSet' 
--   SYNTAX 1.3.6.1.4.1.1466.115.121.1.40 
--  ) 

"pwmResponseSet" ATTRIBUTE ::=
 { 
	 SyntaxID 	 SYN_OCTET_STRING,
	 Flags 	{ DS_SYNC_IMMEDIATE }, 
	 ASN1ObjID 	{ 1 3 6 1 4 1 35015 1 2 2 } 
 }

-- attributeTypes: ( 
--   1.3.6.1.4.1.35015.1.2.3 
--   NAME 'pwmLastPwdUpdate' 
--   SYNTAX 1.3.6.1.4.1.1466.115.121.1.24 
--  ) 

"pwmLastPwdUpdate" ATTRIBUTE ::=
 { 
	 SyntaxID 	 SYN_TIME,
	 Flags 	{ DS_SYNC_IMMEDIATE }, 
	 ASN1ObjID 	{ 1 3 6 1 4 1 35015 1 2 3 } 
 }

-- attributeTypes: ( 
--   1.3.6.1.4.1.35015.1.2.4 
--   NAME 'pwmGUID' 
--   SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 
--  ) 

"pwmGUID" ATTRIBUTE ::=
 { 
	 SyntaxID 	 SYN_CI_STRING,
	 Flags 	{ DS_SYNC_IMMEDIATE }, 
	 ASN1ObjID 	{ 1 3 6 1 4 1 35015 1 2 4 } 
 }

-- attributeTypes: (
--   1.3.6.1.4.1.35015.1.2.5
--   NAME 'pwmToken'
--   SYNTAX 1.3.6.1.4.1.1466.115.121.1.15
--  )

"pwmToken" ATTRIBUTE ::=
 {
	 SyntaxID 	 SYN_CI_STRING,
	 Flags 	{ DS_SYNC_IMMEDIATE },
	 ASN1ObjID 	{ 1 3 6 1 4 1 35015 1 2 5 }
 }

-- objectClasses: ( 1.3.6.1.4.1.35015.1.1.1
--   NAME 'pwmUser' 
--   AUXILIARY 
--   MAY ( pwmLastPwdUpdate $ pwmEventLog $ pwmResponseSet $ pwmGUID $ pwmToken ) )

"pwmUser" OBJECT-CLASS ::=
 { 
	 Operation 	ADD, 
	 Flags 	{DS_AUXILIARY_CLASS}, 
	 MayContain 	{ "pwmLastPwdUpdate", "pwmEventLog", "pwmResponseSet", "pwmGUID", "pwmToken" },
	 ASN1ObjID 	{ 1 3 6 1 4 1 35015 1 1 1 } 
 }

END

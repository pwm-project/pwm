## Important information about JSP modifications and customizations.

Modifying the JSP (and other) files is strongly discouraged.  As new versions of this software are released you will
need to re-modify the updated JSP files with any changes.  Updating this software is important to address
security vulnerabilities and take advantage of improved security defenses and new features.  Modifying JSPs is tempting
and works well in the short term, but almost always causes long term problems and inhibits important upgrades in the
future.

Instead of modifying the JSP files, using custom javascript is a more sustainable approach.  You can add custom
javascript code via the configuration in 'Settings -> User Interface -> Look & Feel -> Embedded Javascript'.  See
the help for that setting to learn more.

Additionally, you can use 'Settings -> User Interface -> Look & Feel -> Custom Resource Bundle' to include file resources
such as images, javascript and css'.  See the help for that setting to learn more.

Using the two settings above it is possible to accomplish nearly anything that modifying the JSPs would do.
Additionally your changes will be part of the configuration and be preserved over upgrades.  Thus, a non-developer
administrator will have a good chance of being able to upgrade the software with the modifications intact.  While there
is not a guarantee of consistent application javascript APIs in this software, the environment is generally stable and
will often (but not always) work without modification from version to version.

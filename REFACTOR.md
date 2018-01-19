= Some notes

* No changes in functionality is done
* DatabaseHelper is now loose coupled to the FoodHelper
* FoodHelper contains specific DataModel Methods for the Food-Table
* The Food Entity knows how to construct itself from JSONObject
* A specific DAO is constructed for Food, which contains several specific CRUD methods
* Some additional improvements not done yet:
    * FoodHelper still contains the EventBus Handling, which should be refactored into the service class
    * Service class should then contain all methods which send events which are right now in the DAO
    * DataService Methods should then call FoodService class methods
    * DatabaseHandler should just contain methods related to data structure and not content, but other helper will not be necessary
    * Exception Handling should then be done in the Service Class (eg. Catch SQLException and send those wrapped to the frontend if appropriate)
    * Circular Dependencies between classes should not happen, can lead to problems during initialization
    * ORMLite seem to provide performance improvements if TableConfig is used
    * Some javadoc would be kinda nice
    * Additional Unit-Test for eg. the Calculation of IOB would be nice

All of this sounds like a lot of unnecessary work, but IMHO it isn't. The application will be easier to maintain and
new features can be added easier. Functionality should be in the best case only require a change in one class.
This provides guidance and structure for new committers as well as all existing committers. All
already huge classes as well as methods will get smaller and be easier to read and maintain. 

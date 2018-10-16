This is a tool for binding tags from Kepserver or the OPC Aggregator to properties on Things. If the Thing doesn't exist, it will be created; if the property exists on the Thing (from the Template) it will be bound to that property; if the property does not exist, it will be created and bound.
Additionally, you can include other information such as a Network, which will be created if it does not exist, Projects, Tags, etc. 

The DataShape of the expected input is called "PTC.Factory.OpcTagMappingDataShape". 
An example of the minimum input and the expected data shape of the InfoTable, as a CSV, can be found in ./data.csv

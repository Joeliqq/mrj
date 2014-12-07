package cn.edu.neu.mitt.mrj.reasoner.rdfs;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.apache.cassandra.thrift.InvalidRequestException;
import org.apache.cassandra.thrift.SchemaDisagreementException;
import org.apache.cassandra.thrift.TimedOutException;
import org.apache.cassandra.thrift.UnavailableException;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.thrift.TException;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.edu.neu.mitt.mrj.data.Triple;
import cn.edu.neu.mitt.mrj.io.dbs.CassandraDB;
import cn.edu.neu.mitt.mrj.utils.NumberUtils;
import cn.edu.neu.mitt.mrj.utils.TriplesUtils;

import com.datastax.driver.core.Row;

//public class RDFSSubPropDomRangeMapper extends Mapper<TripleSource, Triple, LongWritable, LongWritable> {
public class RDFSSubPropDomRangeMapper extends Mapper<Long, Row, BytesWritable, LongWritable> {

	protected static Logger log = LoggerFactory.getLogger(RDFSSubPropDomRangeMapper.class);
	protected Set<Long> domainSchemaTriples = null;
	protected Set<Long> rangeSchemaTriples = null;

	byte[] bKey = new byte[16];	// Added by WuGang, 2010-08-26

	protected LongWritable oValue = new LongWritable(0);
//	protected LongWritable oKey = new LongWritable(0);
	protected BytesWritable oKey = new BytesWritable();	// Modified by WuGang, 2010-08-26

	private int previousExecutionStep = -1;
	private boolean hasSchemaChanged = false;
	
	public void map(Long key, Row row,  Context context) throws IOException, InterruptedException {
		int step = row.getInt(CassandraDB.COLUMN_INFERRED_STEPS);
		if (!hasSchemaChanged && step <= previousExecutionStep)
			return;
		Triple value = CassandraDB.readJustificationFromMapReduceRow(row);


		//Check if the predicate has a domain
		if (domainSchemaTriples.contains(value.getPredicate())) {
			NumberUtils.encodeLong(bKey,0,value.getSubject());	// Added by WuGang, 2010-08-26
			NumberUtils.encodeLong(bKey,8,value.getObject());	// Added by WuGang, 2010-08-26
//			oKey.set(value.getSubject());
			oKey.set(bKey, 0, 16);	// Modified by WuGang, 2010-08-26
			oValue.set(value.getPredicate() << 1);	// 可以通过oValue的最后一位是0来确定，当前处理的是domain
			context.write(oKey, oValue);	// 将<<s,o>, p>发过去, for rule 2
		}

		//Check if the predicate has a range
		if (rangeSchemaTriples.contains(value.getPredicate())
				&& !value.isObjectLiteral()) {
			NumberUtils.encodeLong(bKey,0,value.getObject());	// Added by WuGang, 2010-08-26
			NumberUtils.encodeLong(bKey,8,value.getSubject());	// Added by WuGang, 2010-08-26
//			oKey.set(value.getObject());
			oKey.set(bKey, 0, 16);	// Modified by WuGang, 2010-08-26
			oValue.set((value.getPredicate() << 1) | 1);	// 可以通过oValue的最后一位是1来确定，当前处理的是range
			context.write(oKey, oValue);	// 将<<o, s>, p>发过去, for rule 3
		}
		
	}
	
	@Override
	protected void setup(Context context) throws IOException {		
		hasSchemaChanged = false;
		previousExecutionStep = context.getConfiguration().getInt("lastExecution.step", -1);

		try{
			CassandraDB db = new CassandraDB();
			if (domainSchemaTriples == null) {
				domainSchemaTriples = new HashSet<Long>();
				Set<Integer> filters = new HashSet<Integer>();
				filters.add(TriplesUtils.SCHEMA_TRIPLE_DOMAIN_PROPERTY);
				hasSchemaChanged = db.loadSetIntoMemory(domainSchemaTriples, filters, previousExecutionStep);
			}
			
			if (rangeSchemaTriples == null) {
				rangeSchemaTriples = new HashSet<Long>();
				Set<Integer> filters = new HashSet<Integer>();
				filters.add(TriplesUtils.SCHEMA_TRIPLE_RANGE_PROPERTY);
	
				hasSchemaChanged |= db.loadSetIntoMemory(rangeSchemaTriples, filters, previousExecutionStep);
			}
		}catch(TTransportException tte){
			tte.printStackTrace();
		} catch (InvalidRequestException e) {
			e.printStackTrace();
		} catch (UnavailableException e) {
			e.printStackTrace();
		} catch (TimedOutException e) {
			e.printStackTrace();
		} catch (SchemaDisagreementException e) {
			e.printStackTrace();
		} catch (TException e) {
			e.printStackTrace();
		}
	}
}
